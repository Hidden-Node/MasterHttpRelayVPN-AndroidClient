use std::ptr;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;

use jni::objects::{GlobalRef, JClass, JObject, JString, JValue};
use jni::sys::{jboolean, jstring, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use jni::JavaVM;
use once_cell::sync::Lazy;
use tokio::sync::oneshot;

use mhrv_rs::config::Config;
use mhrv_rs::mitm::MitmCertManager;
use mhrv_rs::proxy_server::ProxyServer;

const STATE_CONNECTING: i32 = 1;
const STATE_CONNECTED: i32 = 2;
const STATE_DISCONNECTED: i32 = 3;
const STATE_ERROR: i32 = 4;

#[derive(Default)]
struct CallbackState {
    vm: Option<JavaVM>,
    callback: Option<GlobalRef>,
}

struct RuntimeState {
    running: AtomicBool,
    stop_tx: Mutex<Option<oneshot::Sender<()>>>,
    worker: Mutex<Option<thread::JoinHandle<()>>>,
}

impl Default for RuntimeState {
    fn default() -> Self {
        Self {
            running: AtomicBool::new(false),
            stop_tx: Mutex::new(None),
            worker: Mutex::new(None),
        }
    }
}

static CALLBACKS: Lazy<Mutex<CallbackState>> = Lazy::new(|| Mutex::new(CallbackState::default()));
static RUNTIME: Lazy<Arc<RuntimeState>> = Lazy::new(|| Arc::new(RuntimeState::default()));

#[no_mangle]
pub extern "system" fn Java_com_masterhttprelay_vpn_bridge_RustBridge_nativeInit(
    env: JNIEnv,
    _class: JClass,
    callback: JObject,
) {
    let vm = match env.get_java_vm() {
        Ok(vm) => vm,
        Err(_) => return,
    };
    let global = match env.new_global_ref(callback) {
        Ok(r) => r,
        Err(_) => return,
    };

    if let Ok(mut state) = CALLBACKS.lock() {
        state.vm = Some(vm);
        state.callback = Some(global);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_masterhttprelay_vpn_bridge_RustBridge_nativeStart(
    mut env: JNIEnv,
    _class: JClass,
    config_json: JString,
) -> jboolean {
    if RUNTIME.running.swap(true, Ordering::SeqCst) {
        log_callback(3, "Rust core is already running");
        return JNI_TRUE;
    }

    let cfg = match env.get_string(&config_json) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => {
            RUNTIME.running.store(false, Ordering::SeqCst);
            fatal_callback("Failed to read config JSON from JNI");
            return JNI_FALSE;
        }
    };

    let (stop_tx, stop_rx) = oneshot::channel::<()>();
    if let Ok(mut guard) = RUNTIME.stop_tx.lock() {
        *guard = Some(stop_tx);
    }

    let runtime = Arc::clone(&RUNTIME);
    let handle = thread::spawn(move || {
        state_callback(STATE_CONNECTING, Some("Initializing Rust core"));

        let parsed: Config = match serde_json::from_str(&cfg) {
            Ok(c) => c,
            Err(e) => {
                runtime.running.store(false, Ordering::SeqCst);
                state_callback(STATE_ERROR, Some("Invalid Rust config"));
                fatal_callback(&format!("Config parse error: {e}"));
                return;
            }
        };

        let rt = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
            Ok(rt) => rt,
            Err(e) => {
                runtime.running.store(false, Ordering::SeqCst);
                state_callback(STATE_ERROR, Some("Failed to create runtime"));
                fatal_callback(&format!("Runtime creation failed: {e}"));
                return;
            }
        };

        rt.block_on(async move {
            let base = mhrv_rs::data_dir::data_dir();
            let mitm = match MitmCertManager::new_in(&base) {
                Ok(m) => m,
                Err(e) => {
                    runtime.running.store(false, Ordering::SeqCst);
                    state_callback(STATE_ERROR, Some("MITM init failed"));
                    fatal_callback(&format!("MITM init failed: {e}"));
                    return;
                }
            };

            let mitm = Arc::new(tokio::sync::Mutex::new(mitm));
            let server = match ProxyServer::new(&parsed, mitm) {
                Ok(server) => server,
                Err(e) => {
                    runtime.running.store(false, Ordering::SeqCst);
                    state_callback(STATE_ERROR, Some("Proxy init failed"));
                    fatal_callback(&format!("Proxy init failed: {e}"));
                    return;
                }
            };

            log_callback(2, "Rust proxy server started");
            state_callback(STATE_CONNECTED, Some("Rust core ready"));

            if let Err(e) = server.run(stop_rx).await {
                state_callback(STATE_ERROR, Some("Rust core terminated with error"));
                fatal_callback(&format!("Rust server error: {e}"));
            } else {
                log_callback(2, "Rust proxy server stopped");
            }

            runtime.running.store(false, Ordering::SeqCst);
            state_callback(STATE_DISCONNECTED, Some("Rust core stopped"));
        });
    });

    if let Ok(mut guard) = RUNTIME.worker.lock() {
        *guard = Some(handle);
    }

    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_com_masterhttprelay_vpn_bridge_RustBridge_nativeStop(
    _env: JNIEnv,
    _class: JClass,
) {
    if let Ok(mut guard) = RUNTIME.stop_tx.lock() {
        if let Some(tx) = guard.take() {
            let _ = tx.send(());
        }
    }

    if let Ok(mut worker_guard) = RUNTIME.worker.lock() {
        if let Some(worker) = worker_guard.take() {
            let _ = worker.join();
        }
    }

    RUNTIME.running.store(false, Ordering::SeqCst);
    state_callback(STATE_DISCONNECTED, Some("Rust core stop requested"));
}

#[no_mangle]
pub extern "system" fn Java_com_masterhttprelay_vpn_bridge_RustBridge_nativeIsRunning(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if RUNTIME.running.load(Ordering::SeqCst) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_com_masterhttprelay_vpn_bridge_RustBridge_nativeGetVersion(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let v = env!("CARGO_PKG_VERSION");
    match env.new_string(v) {
        Ok(s) => s.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn with_callback_env<F>(f: F)
where
    F: FnOnce(&mut JNIEnv, &GlobalRef),
{
    let Ok(state) = CALLBACKS.lock() else {
        return;
    };
    let Some(vm) = state.vm.as_ref() else {
        return;
    };
    let Some(callback) = state.callback.as_ref() else {
        return;
    };
    if let Ok(mut env) = vm.attach_current_thread() {
        f(&mut env, callback);
    };
}

fn state_callback(state: i32, message: Option<&str>) {
    with_callback_env(|env, callback| {
        let msg = message.unwrap_or_default();
        if let Ok(jmsg) = env.new_string(msg) {
            let _ = env.call_method(
                callback.as_obj(),
                "onStateChanged",
                "(ILjava/lang/String;)V",
                &[JValue::Int(state), JValue::Object(&JObject::from(jmsg))],
            );
        }
    });
}

fn log_callback(level: i32, message: &str) {
    with_callback_env(|env, callback| {
        if let Ok(jmsg) = env.new_string(message) {
            let _ = env.call_method(
                callback.as_obj(),
                "onLog",
                "(ILjava/lang/String;)V",
                &[JValue::Int(level), JValue::Object(&JObject::from(jmsg))],
            );
        }
    });
}

fn fatal_callback(message: &str) {
    with_callback_env(|env, callback| {
        if let Ok(jmsg) = env.new_string(message) {
            let _ = env.call_method(
                callback.as_obj(),
                "onFatal",
                "(Ljava/lang/String;)V",
                &[JValue::Object(&JObject::from(jmsg))],
            );
        }
    });
}
