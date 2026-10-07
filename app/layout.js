export const metadata = {
  title: 'AutoReply Server',
  description: 'Open source AI auto-reply server',
};

export default function RootLayout({ children }) {
  return (
    <html lang="vi">
      <body>{children}</body>
    </html>
  );
}
