package com.app2m.demo.im;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "MainActivity";
    private String ip = "192.168.2.100";
    private TextView contentTv;
    // 定义检测SocketChannel的Selector对象
    private Selector mSelector;
    // 客户端SocketChannel
    private SocketChannel mSocketChannel;
    // 定义处理编码、解码的字符集
    private Charset mCharset = Charset.forName("UTF-8");
    private String mData = "";
    private Handler mHandler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message message) {
            switch (message.what) {
                case 0:
                    contentTv.append("连接成功...\n");
                    break;
                case 1:
                    contentTv.append("来自服务端端：" + mData + "\n");
                    break;
            }
            return false;
        }
    });
    private EditText mContentEt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        contentTv = (TextView) findViewById(R.id.content_tv);
        mContentEt = (EditText) findViewById(R.id.content_et);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_conn:
                //Android里面网络操作不能放在UI线程，
                // 所以开启一个线程来测试
                new connectThread().start();
                break;
            case R.id.btn_send:
                new sendMsgThread().start();
                break;
        }
    }

    private class sendMsgThread extends Thread {
        @Override
        public void run() {
            super.run();
            try {
                mSocketChannel.write(mCharset.encode(mContentEt.getText().toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class connectThread extends Thread {
        @Override
        public void run() {
            super.run();
            try {
                mSelector = Selector.open();
                InetSocketAddress inetSocketAddress = new InetSocketAddress(ip, 30000);
                // 调用open方法创建连接到指定主机的SocketChannel
                mSocketChannel = SocketChannel.open(inetSocketAddress);
                mHandler.sendEmptyMessage(0);
                // 设置该SocketChannel以非阻塞方式工作
                mSocketChannel.configureBlocking(false);
                // 将该SocketChannle对象注册到指定的Selector
                mSocketChannel.register(mSelector, SelectionKey.OP_READ);
                // 读取服务端消息
                while (mSelector != null && mSelector.select() > 0) {
                    Set<SelectionKey> selectionKeys = mSelector.selectedKeys();
                    Iterator<SelectionKey> iterator = selectionKeys.iterator();
                    while (iterator.hasNext()) {
                        SelectionKey sk = iterator.next();
                        iterator.remove();
                        if (sk != null && sk.isReadable()) {
                            mSocketChannel = (SocketChannel) sk.channel();
                            ByteBuffer buff = ByteBuffer.allocate(1024);
                            String data = "";
                            while (mSocketChannel.read(buff) > 0) {
                                mSocketChannel.read(buff);
                                buff.flip();
                                data += mCharset.decode(buff);
                                buff.clear();
                            }
                            Log.e(TAG, "run: data:" + data);
                            mData = data;
                            mHandler.sendEmptyMessage(1);
                            sk.interestOps(SelectionKey.OP_READ);
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "run: e:" + e.getMessage());
            }
        }
    }
}