export const metadata = {
  title: 'AutoReply OSS',
  description: 'Open source AI auto-reply server',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
