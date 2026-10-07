# AutoReply OSS

Open source AI auto-reply bot for Android.

## Cấu trúc
- `server/` - Server Next.js (deploy lên Vercel)
- `android/` - App Android (build APK)

## Cài đặt Server
1. Fork repo này
2. Vào https://vercel.com/new, import repo
3. **Root Directory:** chọn `server`
4. Tạo KV Storage (Upstash for Redis - Free)
5. Thêm Environment Variables:
   - `BOT_SECRET` = chuỗi bí mật tự đặt
   - `GROQ_API_KEY` = key từ console.groq.com
   - `GEMINI_API_KEY` = key từ Google AI Studio
   - `CF_ACCOUNT_ID`, `CF_API_TOKEN` = Cloudflare Workers AI
   - `GITHUB_REPO` = username/autoreply-oss
6. Redeploy

## Build App Android
1. Mở Android Studio, import thư mục `android/`
2. Sửa `app/build.gradle.kts`:
   - `SERVER_URL` = URL Vercel của bạn
   - `BOT_SECRET` = chuỗi bạn đặt ở bước trên
3. Build APK
4. Cài lên máy, bật quyền thông báo

## License
MIT
