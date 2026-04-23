#!/usr/bin/env python3
"""
Android VPN Core Wrapper

This module provides a simplified interface for the Android app to control
the Python-based VPN proxy server. It wraps the main proxy server logic
and exposes lifecycle methods that can be called from Kotlin via Chaquopy.
"""

import asyncio
import json
import logging
import os
import sys
import threading
from typing import Optional, Any

# Add src directory to path
_SRC_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "src")
if _SRC_DIR not in sys.path:
    sys.path.insert(0, _SRC_DIR)

from proxy_server import ProxyServer
from logging_utils import configure as configure_logging
import mitm


class VpnCore:
    """Main VPN core controller for Android integration."""
    
    def __init__(self):
        self.server: Optional[ProxyServer] = None
        self.loop: Optional[asyncio.AbstractEventLoop] = None
        self.thread: Optional[threading.Thread] = None
        self.running = False
        self.callback: Optional[Any] = None
        self._lock = threading.Lock()
        
    def set_callback(self, callback: Any):
        """Set callback for state changes and logs."""
        self.callback = callback
        
    def _log(self, level: int, message: str):
        """Send log to callback if available."""
        if self.callback:
            try:
                self.callback.onLog(level, message)
            except Exception as e:
                logging.error(f"Callback error: {e}")
                
    def _state_changed(self, state: int, message: str = ""):
        """Send state change to callback if available."""
        if self.callback:
            try:
                self.callback.onStateChanged(state, message if message else None)
            except Exception as e:
                logging.error(f"Callback error: {e}")
                
    def _fatal(self, message: str):
        """Send fatal error to callback if available."""
        if self.callback:
            try:
                self.callback.onFatal(message)
            except Exception as e:
                logging.error(f"Callback error: {e}")
    
    def start(self, config_json: str, ca_dir: str) -> bool:
        """
        Start the VPN proxy server.
        
        Args:
            config_json: JSON string containing configuration
            ca_dir: Directory path for CA certificate storage
            
        Returns:
            True if started successfully, False otherwise
        """
        with self._lock:
            if self.running:
                self._log(3, "VPN core already running")
                return False
                
            try:
                # Parse config
                config = json.loads(config_json)
                
                # Set up logging
                log_level = config.get("log_level", "INFO").upper()
                configure_logging(log_level)
                
                # Set up CA certificate directory
                os.makedirs(ca_dir, exist_ok=True)
                ca_cert_path = os.path.join(ca_dir, "ca.crt")
                ca_key_path = os.path.join(ca_dir, "ca.key")

                # Point MITM module to Android app-private CA directory.
                mitm.CA_DIR = ca_dir
                mitm.CA_CERT_FILE = ca_cert_path
                mitm.CA_KEY_FILE = ca_key_path

                # Generate CA if not exists (via MITM manager).
                if not os.path.exists(ca_cert_path) or not os.path.exists(ca_key_path):
                    self._log(2, "Generating CA certificate...")
                    mitm.MITMCertManager()
                    self._log(2, f"CA certificate generated at {ca_cert_path}")
                
                # Override CA paths in environment
                os.environ['CA_CERT_FILE'] = ca_cert_path
                os.environ['CA_KEY_FILE'] = ca_key_path
                
                # Validate required config
                if not config.get("auth_key"):
                    raise ValueError("Missing auth_key in config")
                    
                script_id = config.get("script_id") or config.get("script_ids")
                if not script_id:
                    raise ValueError("Missing script_id in config")
                
                # State: CONNECTING
                self._state_changed(1, "Starting VPN core...")
                self._log(2, f"Starting proxy server on {config.get('listen_host', '127.0.0.1')}:{config.get('listen_port', 8080)}")
                
                # Start server in background thread
                self.thread = threading.Thread(target=self._run_server, args=(config,), daemon=True)
                self.thread.start()
                
                # Wait a bit for server to start
                import time
                time.sleep(1)
                
                if self.running:
                    self._state_changed(2, "VPN core started")
                    self._log(2, "Proxy server running")
                    return True
                else:
                    self._state_changed(4, "Failed to start VPN core")
                    return False
                    
            except Exception as e:
                error_msg = f"Failed to start VPN core: {str(e)}"
                self._log(4, error_msg)
                self._state_changed(4, error_msg)
                self._fatal(error_msg)
                return False
    
    def _run_server(self, config: dict):
        """Run the proxy server in asyncio event loop."""
        try:
            # Create new event loop for this thread
            self.loop = asyncio.new_event_loop()
            asyncio.set_event_loop(self.loop)
            
            # Create and start server
            self.server = ProxyServer(config)
            self.running = True
            
            # Run server
            self.loop.run_until_complete(self.server.start())
            
        except Exception as e:
            error_msg = f"Server error: {str(e)}"
            self._log(4, error_msg)
            self._fatal(error_msg)
            self.running = False
        finally:
            if self.loop:
                try:
                    self.loop.close()
                except:
                    pass
            self.running = False
            self._state_changed(3, "VPN core stopped")
    
    def stop(self):
        """Stop the VPN proxy server."""
        with self._lock:
            if not self.running:
                return
                
            self._log(2, "Stopping VPN core...")
            self._state_changed(3, "Stopping...")
            
            try:
                if self.server and self.loop:
                    # Schedule stop in the server's event loop
                    asyncio.run_coroutine_threadsafe(self.server.stop(), self.loop)
                    
                self.running = False
                
                # Wait for thread to finish
                if self.thread and self.thread.is_alive():
                    self.thread.join(timeout=5)
                    
                self._log(2, "VPN core stopped")
                self._state_changed(3, "Disconnected")
                
            except Exception as e:
                error_msg = f"Error stopping VPN core: {str(e)}"
                self._log(4, error_msg)
    
    def is_running(self) -> bool:
        """Check if the VPN core is running."""
        return self.running
    
    def get_version(self) -> str:
        """Get the VPN core version."""
        try:
            from constants import __version__
            return f"Python Core v{__version__}"
        except:
            return "Python Core v1.0.0"


# Global instance
_core_instance: Optional[VpnCore] = None


def get_instance() -> VpnCore:
    """Get or create the global VPN core instance."""
    global _core_instance
    if _core_instance is None:
        _core_instance = VpnCore()
    return _core_instance


# Convenience functions for Chaquopy
def init(callback):
    """Initialize VPN core with callback."""
    core = get_instance()
    core.set_callback(callback)


def start(config_json: str, ca_dir: str) -> bool:
    """Start VPN core."""
    core = get_instance()
    return core.start(config_json, ca_dir)


def stop():
    """Stop VPN core."""
    core = get_instance()
    core.stop()


def is_running() -> bool:
    """Check if VPN core is running."""
    core = get_instance()
    return core.is_running()


def get_version() -> str:
    """Get VPN core version."""
    core = get_instance()
    return core.get_version()


def ensure_ca(ca_dir: str) -> str:
    """Ensure CA cert/key exist and return ca.crt path."""
    os.makedirs(ca_dir, exist_ok=True)
    ca_cert_path = os.path.join(ca_dir, "ca.crt")
    ca_key_path = os.path.join(ca_dir, "ca.key")

    mitm.CA_DIR = ca_dir
    mitm.CA_CERT_FILE = ca_cert_path
    mitm.CA_KEY_FILE = ca_key_path

    if not os.path.exists(ca_cert_path) or not os.path.exists(ca_key_path):
        mitm.MITMCertManager()
    return ca_cert_path
