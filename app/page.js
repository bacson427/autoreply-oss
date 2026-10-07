export default function Home() {
  return (
    <main style={{ fontFamily: 'sans-serif', padding: 40 }}>
      <h1>🤖 AutoReply Server</h1>
      <p>Open source AI auto-reply server đang chạy.</p>
      <ul>
        <li>POST /api/reply — Nhận tin nhắn, trả lời tự động</li>
        <li>POST /api/logs — Lưu log (tự xóa sau 7 ngày)</li>
        <li>GET /api/version — Check bản cập nhật app Android</li>
      </ul>
    </main>
  );
}
