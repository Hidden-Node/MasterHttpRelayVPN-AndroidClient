# ساختار عمومی پروژه (مشابه go)

این پروژه با ساختار مشابه `go` آماده شده است:

- `android/` : اپ اندروید + گرادل + اسکریپت‌های بیلد موبایل
- ریشه پروژه: هسته Rust (`Cargo.toml`, `src/`, `assets/`, ...)
- `.github/workflows/` : CI و Manual Release

## روند کاری دقیقا مثل پروژه go

## 1) وقتی هسته آپدیت شد

فقط فایل‌های هسته در ریشه را جایگزین کن (مثل:
`src/`, `Cargo.toml`, `Cargo.lock`, `assets/`, `config.example.json`).

نکته: فولدر `android/` را تغییر نده مگر این‌که API/رفتار هسته عوض شده باشد.

## 2) برای تست روی GitHub

- کد را push کن.
- workflow `Android CI` خودش اجرا می‌شود.
- خروجی APK در artifacts قرار می‌گیرد.

## 3) برای انتشار نسخه جدید (Manual Release)

- به `Actions > Manual Release` برو.
- ورودی‌ها:
  - `tag_name`: مثل `v1.2.3`
  - `release_name`: اختیاری
  - `make_latest`: true/false

این workflow:
- بیلد Rust و tun2socks را انجام می‌دهد
- APK ریلیز ساین‌شده می‌سازد
- فایل‌ها را روی GitHub Release آپلود می‌کند

## وضعیت ABI در این نسخه

- خروجی AAR برای tun2socks فعلاً روی `arm64` تنظیم شده تا بیلد GitHub پایدار و قابل انتشار باشد.
- بنابراین APK ریلیز برای دستگاه‌های `arm64-v8a` آماده می‌شود (اکثر گوشی‌های اندروید جدید).

## Secrets موردنیاز (مثل go)

در GitHub repo این secrets را ست کن:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

مسیر: `Settings > Secrets and variables > Actions`

## اسکریپت مرکزی بیلد موبایل

برای بیلد محلی مشابه go:

```bash
bash android/build_rust_mobile.sh
```

این اسکریپت:
- `tun2socks.aar` را می‌سازد
- باینری‌های Rust اندروید را می‌سازد
