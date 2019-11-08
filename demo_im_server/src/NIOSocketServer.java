import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class NIOSocketServer implements ActionListener {

    private String ip = "192.168.2.100";
    private Window mWindow;
    private JButton mBtnSend;
    private JButton mBtnSendAll;
    private JTextField mTextFiled;
    private JTextArea mTextArea;
    private JButton mBtnClear;
    // 用于检测所有Channel状态的Selector
    private Selector mSelector = null;
    // 定义实现编码、解码的字符集对象
    private Charset mCharset = Charset.forName("UTF-8");
    private SocketChannel mSocketChannel = null;
    // private static ExecutorService mThreadPool;
    // private static SocketRun mSocketRun;

    public NIOSocketServer() {
        mWindow = new Window("服务端");
        mBtnSend = mWindow.getSendButton();
        mBtnSend.setName("发送");
        mBtnSendAll = mWindow.getSendAllButton();
        mBtnSendAll.setName("群发");
        mBtnClear = mWindow.getClearButton();
        mBtnClear.setName("clear");
        mTextFiled = mWindow.getTextField();
        mTextArea = mWindow.getJTextArea();
        mBtnSend.addActionListener(this);
        mBtnSendAll.addActionListener(this);
        mBtnClear.addActionListener(this);
        // mSocketRun = new SocketRun();
        // mThreadPool = Executors.newCachedThreadPool();
    }

    public static void main(String[] args) throws IOException {
        System.out.println("服务端已启动...");
        new NIOSocketServer().init();
    }

    @Override
    public void actionPerformed(ActionEvent event) {
        JButton source = (JButton) event.getSource();
        String name = source.getName();
        if (mBtnSend.getName().equals(name)) { // 向单个客户端发送消息
            String content = mTextFiled.getText().toString();
            if (mSocketChannel != null) {
                try {
                    mSocketChannel.write(mCharset.encode(content));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if (mBtnSendAll.getName().equals(name)) { // 向所有客户端发送消息
            String content = mTextFiled.getText().toString();
            sendToAll(content);
        } else if (mBtnClear.getName().equals(name)) {
            mTextArea.setText("");
        }
    }

    private void sendToAll(String message) {

        for (SelectionKey sk : mSelector.keys()) {
            Channel channel = sk.channel();
            if (channel instanceof SocketChannel) {
                SocketChannel dest = (SocketChannel) channel;
                if (dest.isOpen()) {
                    try {
                        dest.write(mCharset.encode(message));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void init() throws IOException {
        mSelector = Selector.open();
        // 通过open方法打开一个未绑定的ServerSocketChannel实例
        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        InetSocketAddress inetSocketAddress = new InetSocketAddress(ip, 30000);
        // 将该ServerSocketChannel绑定到指定的IP地址
        serverSocketChannel.socket().bind(inetSocketAddress);
        // 设置ServerSocket以非阻塞方式工作
        serverSocketChannel.configureBlocking(false);
        // 将serverSocketChannel注册到指定Selector对象
        serverSocketChannel.register(mSelector, SelectionKey.OP_ACCEPT);
        while (mSelector.select() > 0) {
            // 依次处理selector上的每个已选择的SelectionKey
            Set<SelectionKey> selectedKeys = mSelector.selectedKeys();
            //这里必须用iterator，如果用for遍历Set程序会报错
            Iterator<SelectionKey> iterator = selectedKeys.iterator();
            while (iterator.hasNext()) {
                SelectionKey sk = iterator.next();
                // 从selector上的已选择的SelectionKey集合中删除正在处理的SelectionKey
                iterator.remove();
                // 如果sk对应的通道包含客户端的连接请求
                if (sk.isAcceptable()) {
                    // 调用accept方法接受连接，产生服务端对应的SocketChannel
                    SocketChannel socketChannel = serverSocketChannel.accept();
                    mTextArea.append("客户端接入，IP："+socketChannel.getLocalAddress()+"\n");
                    // 设置采用非阻塞模式
                    socketChannel.configureBlocking(false);
                    mSocketChannel = socketChannel;
                    // 将该SocketChannel也注册到selector
                    socketChannel.register(mSelector, SelectionKey.OP_READ);
                    // 将sk对应的Channel设置成准备接受其他请求
                    sk.interestOps(SelectionKey.OP_ACCEPT);

                }
                // 如果sk对应的通道有数据需要读取
                if (sk.isReadable()) {
                    // 获取该SelectionKey对应的Channel，该Channel中有可读的数据
                    SocketChannel socketChannel = (SocketChannel) sk.channel();
                    mSocketChannel = socketChannel;
                    // 定义准备执行读取数据的ByteBuffer
                    ByteBuffer buffer = ByteBuffer.allocate(1024);
                    String content = "";
                    // 开始读取数据
                    try {
                        while (socketChannel.read(buffer) > 0) {
                            buffer.flip();
                            content += mCharset.decode(buffer);
                        }
                        if ("shutdown".equals(content)) {
                            sk.cancel();
                            if (sk.channel() != null) {
                                sk.channel().close();
                            }
                            sendToAll("reconnect");
                        } else {
                            // 打印从该sk对应的Channel里读取到的数据
                            mTextArea.append("来自客户端的消息：" + content + "\n");
                            // 将sk对应的Channel设置成准备下一次读取
                            sk.interestOps(SelectionKey.OP_READ);
                        }
                    }
                    // 如果捕捉到该sk对应的Channel出现了异常，即表明该Channel
                    // 对应的Client出现了问题，所以从Selector中取消sk的注册
                    catch (IOException ex) {
                        // 从Selector中删除指定的SelectionKey
                        sk.cancel();
                        if (sk.channel() != null) {
                            sk.channel().close();
                        }
                        sendToAll("reconnect");
                    }
                }
            }
        }
    }
}