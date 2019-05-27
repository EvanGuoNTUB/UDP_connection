// Android IP多点组播MulticastSocket
// https://blog.csdn.net/androiddeveloper_lee/article/details/9299135
package com.abc;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.MulticastSocket;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.net.UnknownHostException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    EditText editTextMultiAddress, editTextLocalAddress;
    Button mbtnConnect , mbtnCancel , mbtnConsole , mbtnStart;
    TextView textViewState, textViewRx;

    StaticHandler mhandler;
    MulticastServerThread multicastServerThread;
    MulticastSendThread multicastSendThread;
    UDPReadThread udpReadThread;
    UDPSendThread udpSendThread;

    DatagramSocket UDPsocket;
    MulticastSocket socket;

    protected static final String TAG = "MultiSocketA";
    String localAddress , hostAddress;
    String multicastAddress = "224.1.2.3";
    int multicastPort = 6789;
    int localUDPPort = 5678;
    List<String> playerAddress;

    String recvStr , sendStr;
    Boolean isHost = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        editTextMultiAddress = (EditText) findViewById(R.id.multiaddress);
        editTextLocalAddress = (EditText) findViewById(R.id.localaddress);
        mbtnConnect = (Button) findViewById(R.id.btnConnect);
        mbtnCancel = (Button) findViewById(R.id.btnCancel);
        mbtnConsole = (Button) findViewById(R.id.btnConsole);
        mbtnStart = (Button) findViewById(R.id.btnStart);
        textViewState = (TextView)findViewById(R.id.state);
        textViewRx = (TextView)findViewById(R.id.received);

        mbtnConnect.setOnClickListener(mbtnConnectOnClickListener);
        mbtnCancel.setOnClickListener(mbtnCancelOnClickListener);
        mbtnConsole.setOnClickListener(mbtnConsoleOnClickListener);
        mbtnStart.setOnClickListener(mbtnStartOnClickListener);

        localAddress = getLocalAddress();
        editTextMultiAddress.setText(multicastAddress);
        editTextLocalAddress.setText(String.valueOf(localAddress));
        //textViewState.setText(localAddress + "  ");

        mhandler = new StaticHandler(this);
    }

    View.OnClickListener mbtnConnectOnClickListener = new View.OnClickListener() {

                @Override
                public void onClick(View arg0) {

                    //mhandler = new StaticHandler(MainActivity.this);
                    isHost = false;
                    try {
                        UDPsocket = new DatagramSocket(localUDPPort);
                    } catch (SocketException e) {
                        e.printStackTrace();
                     } catch (IOException e) {
                        e.printStackTrace();
                    }

                    sendStr = "報名:" + localAddress + "\n";

                    udpReadThread = new UDPReadThread(multicastAddress , multicastPort , mhandler);
                    udpReadThread.start();

                    udpSendThread = new UDPSendThread(multicastAddress , multicastPort , mhandler);
                    udpSendThread.start();

                    mbtnConnect.setEnabled(false);
                    mbtnCancel.setEnabled(true);
                    mbtnConsole.setEnabled(false);
                    mbtnStart.setEnabled(false);
                }
     };

    View.OnClickListener mbtnCancelOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {

            if (isHost == false){
                sendStr = "取消報名:" + localAddress + "\n";

                if (udpSendThread != null)
                    udpSendThread = null;
                udpSendThread = new UDPSendThread(multicastAddress , multicastPort , mhandler);
                udpSendThread.start();
                try{
                    udpSendThread.join();
//                    udpReadThread.running = false;   // 加這2行 , 動作變很慢怪怪的
//                    udpReadThread.join();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }else {
                // 通知各玩家開始遊戲
                sendStr = "取消遊戲:" + localAddress + "\n";
                if (multicastSendThread != null)
                    multicastSendThread = null;
                multicastSendThread = new MulticastSendThread(multicastAddress , multicastPort , mhandler);
                multicastSendThread.start();

                try{
                    multicastSendThread.join();
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
            }
            ResetAll();
        }
    };

    View.OnClickListener mbtnConsoleOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {

            hostAddress = localAddress;
            playerAddress = new ArrayList<String>();
            //playerAddress.add(localAddress);

             isHost = true;
            try {
                socket = new MulticastSocket(multicastPort);
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            multicastServerThread = new MulticastServerThread(multicastAddress , multicastPort , mhandler);
            multicastServerThread.start();

            mbtnConnect.setEnabled(false);
            mbtnCancel.setEnabled(true);
            mbtnConsole.setEnabled(false);
            mbtnStart.setEnabled(true);
        }
    };

    View.OnClickListener mbtnStartOnClickListener = new View.OnClickListener() {

        @Override
        public void onClick(View arg0) {
            // 通知各玩家開始遊戲
            sendStr = "開始遊戲:" + hostAddress + "\n";

            if (multicastSendThread != null)
                multicastSendThread = null;
            multicastSendThread = new MulticastSendThread(multicastAddress , multicastPort , mhandler);
            multicastSendThread.start();

            try{
                multicastSendThread.join();
            } catch (InterruptedException e){
                e.printStackTrace();
            }

            // 啟動主控機玩遊戲的機動程式  *****************************************



//            mbtnConnect.setEnabled(false);
//            mbtnCancel.setEnabled(true);
//            mbtnConsole.setEnabled(false);
//            mbtnStart.setEnabled(true);
        }
    };

    private void clientEnd(){
        multicastServerThread = null;
        //textViewState.setText("clientEnd()");
        textViewState.append("clientEnd()");
        mbtnConnect.setEnabled(true);

    }

    public static class StaticHandler extends Handler {
        public static final int UPDATE_STATE = 0;
        public static final int UPDATE_MSG = 1;
        public static final int UPDATE_END = 2;
        private WeakReference<MainActivity> mActivity;

        public StaticHandler(MainActivity activity) {
            super();
            this.mActivity = new WeakReference<MainActivity> (activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mActivity.get();
            if (activity == null) return;
            
            switch (msg.what){
                case UPDATE_STATE:
                    activity.updateState((String)msg.obj);
                    break;
                case UPDATE_MSG:
                    if (((String)msg.obj).startsWith("開始遊戲:")){
                        // 啟動玩家玩遊戲的機動程式  *****************************************



                    } else if (((String)msg.obj).startsWith("取消遊戲:")){
                        activity.ResetAll();
                    } else if (((String)msg.obj).startsWith("報名:")){
                        //activity.editTextLocalAddress.setText("收到 報名:");
                        String tmp[] = ((String)msg.obj).split(":");
                        if (activity.playerAddress.indexOf(tmp[1]) < 0)
                            activity.playerAddress.add(tmp[1]);
                    } else if (((String)msg.obj).startsWith("取消報名:")){
                        //Log.v(TAG, "before playerAddress.remove");
                        String tmp[] = ((String)msg.obj).split(":");
                        if (activity.playerAddress.indexOf(tmp[1]) > -1)
                            activity.playerAddress.remove(tmp[1]);
                    }
                    activity.updateRxMsg((String)msg.obj);
                    //activity.recvStr = "";
                    break;
                case UPDATE_END:
                    activity.clientEnd();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private class MulticastServerThread extends Thread{

        String dstAddress;
        int dstPort;
        private boolean running;
        StaticHandler handler;

        //public DatagramSocket socket;
        public InetAddress address;

        public MulticastServerThread(String addr, int port, StaticHandler handler) {
            super();
            dstAddress = addr;
            dstPort = port;
            this.handler = handler;
        }

        public void setRunning(boolean running){
            this.running = running;
        }

        private void sendState(String state){
            handler.sendMessage(Message.obtain(handler, handler.UPDATE_STATE, state));
        }

        @Override
        public void run() {
            sendState("connecting...");

                try {
                    //socket = new DatagramSocket();
                    address = InetAddress.getByName(dstAddress);
                    //socket = new MulticastSocket(multicastPort);
                    socket.setLoopbackMode(true);
                    socket.joinGroup(address);

                    running = true;

                    while (running) {
                        // get request
                        byte[] recvbuf = new byte[512];
                        DatagramPacket packet = new DatagramPacket(recvbuf, recvbuf.length);
                        socket.receive(packet);

                        InetAddress  remoteAddress = packet.getAddress();
                        int remotePort = packet.getPort();

                        recvStr = new String(packet.getData(), 0, packet.getLength());
                        recvStr = recvStr.trim();
                                //recvStr = new String(packet.getData()).trim() ;

                        handler.sendMessage(Message.obtain(handler, handler.UPDATE_MSG, recvStr));
                        //mhandler.sendMessage(Message.obtain(mhandler, mhandler.UPDATE_MSG, recvStr));

                        // send response
                        if (recvStr.startsWith("報名:"))
                           sendStr = "已報名:" + packet.getAddress().getHostAddress() + "\n";
                        else if (recvStr.startsWith("取消報名:"))
                            sendStr = "已取消報名:" + packet.getAddress().getHostAddress() + "\n";

                        byte[] sendbuf = sendStr.getBytes();
                        //packet = new DatagramPacket(sendbuf, sendbuf.length, address, multicastPort);
                        packet = new DatagramPacket(sendbuf, sendbuf.length, remoteAddress, localUDPPort);
                        //packet = new DatagramPacket(sendbuf, sendbuf.length, remoteAddress, remotePort);
                        socket.send(packet);
                        sendState("connected");

//                        if (playerAddress.indexOf(packet.getAddress().getHostAddress()) < 0)
//                            playerAddress.add(packet.getAddress().getHostAddress());

//                        // clear buffer
//                        byte[] clearbuf = new byte[512];
//                        packet = new DatagramPacket(clearbuf, clearbuf.length);
//                        socket.receive(packet);
                    }
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    private class MulticastSendThread extends Thread{

        String dstAddress;
        int dstPort;
        private boolean running;
        StaticHandler handler;

        //public DatagramSocket socket;
        public InetAddress address;

        public MulticastSendThread(String addr, int port, StaticHandler handler) {
            super();
            dstAddress = addr;
            dstPort = port;
            this.handler = handler;
        }

        public void setRunning(boolean running){
            this.running = running;
        }

        private void sendState(String state){
            handler.sendMessage(Message.obtain(handler, handler.UPDATE_STATE, state));
            //mhandler.sendMessage(Message.obtain(mhandler,mhandler.UPDATE_STATE, state));
        }

        @Override
        public void run() {
            sendState("connecting...");

//             if (socket == null  || playerAddress.size() == 0)
//                return;

            int cnt = 0;
            while(multicastServerThread.running == false && cnt <= 15) {
                try {
                    Thread.sleep(500);
                }catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cnt += 1;
            }

            if (cnt > 15)
                Log.v(TAG, "multicastServerThread Not Running");
            //Toast.makeText(MainActivity.this , "無法連線....." , Toast.LENGTH_LONG);

            cnt = 0;
            while(cnt < playerAddress.size()){
                sendState("Send Player Start Game");
                try {
                    address = InetAddress.getByName(playerAddress.get(cnt).trim());
                    sendState("client address = " + playerAddress.get(cnt).trim());
                    // 傳送主動通知
                    //sendStr = "開始遊戲:" + localAddress + "\n";
                    byte[] sendbuf = sendStr.getBytes();
                    DatagramPacket packet = new DatagramPacket(sendbuf, sendbuf.length, address, localUDPPort);
                    socket.send(packet);
                    sendState("connected");
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                cnt += 1;
            }
        }
    }

    private class UDPReadThread extends Thread{
        public String recvResp = "";

        String dstAddress;
        int dstPort;
        private boolean running;
        StaticHandler handler;

        //public DatagramSocket socket;
        public InetAddress address;

        public UDPReadThread(String addr, int port, MainActivity.StaticHandler handler) {
            super();
            dstAddress = addr;
            dstPort = port;
            this.handler = handler;
        }

        public void setRunning(boolean running){
            this.running = running;
        }

        private void sendState(String state){
            handler.sendMessage(Message.obtain(handler, handler.UPDATE_STATE, state));
            //mhandler.sendMessage(Message.obtain(mhandler,mhandler.UPDATE_STATE, state));
        }

        @Override
        public void run() {
            sendState("connecting...");

            try {
                address = InetAddress.getByName(multicastAddress);
                //socket = new DatagramSocket();
                //socket = new MulticastSocket(multicastPort);
                //socket.setLoopbackMode(true);
                //socket.joinGroup(address);

                running = true;
                while(running){
                    //接收資料
                    Log.v(TAG, "receiver packet");
                    byte[] recvbuf = new byte[512];
                    DatagramPacket packet = new DatagramPacket(recvbuf, recvbuf.length);
                    UDPsocket.receive(packet);
                    recvStr = new String(packet.getData(), 0, packet.getLength());
                    recvStr = recvStr.trim();

                    if (recvStr.startsWith("已報名:") || recvStr.startsWith("已取消報名"))
                        recvResp = "OK";

                    handler.sendMessage(Message.obtain(handler, handler.UPDATE_MSG, recvStr));
                    //mhandler.sendMessage(Message.obtain(mhandler, mhandler.UPDATE_MSG, recvStr));

                    Log.v(TAG, "UDPReadThread Read = " + new String(packet.getData()).trim());  	//不加trim，则会打印出512个byte，后面是乱码

                }
            } catch (SocketException e) {
                e.printStackTrace();
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class UDPSendThread extends Thread{

        String dstAddress;
        int dstPort;
        private boolean running;
        StaticHandler handler;

        //public DatagramSocket socket;
        public InetAddress address;

        public UDPSendThread(String addr, int port, MainActivity.StaticHandler handler) {
            super();
            dstAddress = addr;
            dstPort = port;
            this.handler = handler;
        }

        public void setRunning(boolean running){
            this.running = running;
        }

        private void sendState(String state){
            handler.sendMessage(Message.obtain(handler, handler.UPDATE_STATE, state));
            //mhandler.sendMessage(Message.obtain(mhandler,mhandler.UPDATE_STATE, state));
        }

        @Override
        public void run() {
            sendState("connecting...");

            //running = true;
            //while(! udpReadThread.isAlive() || udpReadThread.running == false) {
            if (UDPsocket == null)
                return;

            int cnt = 0;
            while(udpReadThread.running == false && cnt <= 15) {
                try {
                    Thread.sleep(500);
                }catch (InterruptedException e) {
                    e.printStackTrace();
                }
                cnt += 1;
            }

            if (cnt > 15)
                Log.v(TAG, "無法連線.....");
                //Toast.makeText(MainActivity.this , "無法連線....." , Toast.LENGTH_LONG);

            cnt = 0;
            do {
                try {
                    address = InetAddress.getByName(multicastAddress);

                    //傳送資料
                    Log.v(TAG, "send packet");
                    //String snedStr = "報名:" + localAddress + "\n";
                    byte[] sendbuf = sendStr.getBytes();
                    DatagramPacket packet = new DatagramPacket(sendbuf, sendbuf.length, address, multicastPort);
                    UDPsocket.send(packet);

                    running = true;
                } catch (SocketException e) {
                    e.printStackTrace();
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                cnt += 1;
                try {
                    Thread.sleep(500);
                }catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (!udpReadThread.recvResp.startsWith("OK") && cnt <= 15);

            sendStr = "";
            udpReadThread.recvResp = "";
            if (cnt > 15) {
                Log.v(TAG, "無法報名.....");
                // 連線怪怪的 , 看怎麼處裡
            }
        }
    }

    private void updateState(String state){
        textViewState.setText(state);
        //textViewState.append(state + "\n");
    }

    private void updateRxMsg(String rxmsg){
        textViewRx.append(rxmsg + "\n");
    }

    private void ResetAll(){
        localAddress = getLocalAddress();
        hostAddress = "";
        recvStr = "";
        sendStr = "";
        isHost = false;

        if (playerAddress != null){
            playerAddress.clear();
            playerAddress = null;
        }
        if (multicastServerThread != null) {
            multicastServerThread.running = false;
            multicastServerThread = null;
        }
        if (multicastSendThread != null) {
            //multicastSendThread.running = false;
            multicastSendThread = null;
        }
        if (udpReadThread != null) {
            udpReadThread.running = false;
            udpReadThread = null;
        }
        if (udpSendThread != null) {
            //udpSendThread.running = false;
            udpSendThread = null;
        }
//        if (mhandler != null)
//            mhandler = null;

        if (socket != null) {
            try {
                socket.leaveGroup(InetAddress.getByName(multicastAddress));
                socket.close();
                socket = null;
            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (UDPsocket != null) {
            UDPsocket.close();
            UDPsocket = null;
        }

        textViewState.setText("");
        textViewRx.setText("");

        mbtnConnect.setEnabled(true);
        mbtnCancel.setEnabled(false);
        mbtnConsole.setEnabled(true);
        mbtnStart.setEnabled(false);
    }

    private String getLocalAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    //if (inetAddress.isSiteLocalAddress())
                    if (inetAddress.isSiteLocalAddress() && inetAddress instanceof Inet4Address) {
                         //ip += "Local IP Address : " + inetAddress.getHostAddress() ;
                        ip = inetAddress.getHostAddress() ;
                    }
                }
            }
        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }
        return ip;
    }

//    private void sendMultiBroadcast() throws IOException {
//        Log.v(TAG, "sendMultiBroadcast...");
//        /*
//         * 实现多点广播时，MulticastSocket类是实现这一功能的关键，当MulticastSocket把一个DatagramPacket发送到多点广播IP地址，
//         * 该数据报将被自动广播到加入该地址的所有MulticastSocket。MulticastSocket类既可以将数据报发送到多点广播地址，
//         * 也可以接收其他主机的广播信息
//         */
//        socket = new MulticastSocket(8600);
//        //IP协议为多点广播提供了这批特殊的IP地址，这些IP地址的范围是224.0.0.0至239.255.255.255
//        InetAddress address = InetAddress.getByName("224.0.0.1");
//        /*
//         * 创建一个MulticastSocket对象后，还需要将该MulticastSocket加入到指定的多点广播地址，
//         * MulticastSocket使用jionGroup()方法来加入指定组；使用leaveGroup()方法脱离一个组。
//         */
//        socket.joinGroup(address);
//
//        /*
//         * 在某些系统中，可能有多个网络接口。这可能会对多点广播带来问题，这时候程序需要在一个指定的网络接口上监听，
//         * 通过调用setInterface可选择MulticastSocket所使用的网络接口；
//         * 也可以使用getInterface方法查询MulticastSocket监听的网络接口。
//         */
//        //socket.setInterface(address);
//
//        DatagramPacket packet;
//        //发送数据包
//        Log.v(TAG, "send packet");
//        byte[] buf = "Hello I am MultiSocketA".getBytes();
//        packet = new DatagramPacket(buf, buf.length, address, 8601);
//        socket.send(packet);
//
//        //接收数据
//        Log.v(TAG, "receiver packet");
//        byte[] rev = new byte[512];
//        packet = new DatagramPacket(rev, rev.length);
//        socket.receive(packet);
//        Log.v(TAG, "get data = " + new String(packet.getData()).trim());  	//不加trim，则会打印出512个byte，后面是乱码

//        ---------------------
//        作者：androiddeveloper
//        来源：CSDN
//        原文：https://blog.csdn.net/androiddeveloper_lee/article/details/9299135
//        版权声明：本文为博主原创文章，转载请附上博文链接！
//        ---------------------

//        //退出组播
//        socket.leaveGroup(address);
//        socket.close();
//    }
}

//https://blog.csdn.net/wirelessqa/article/details/8589200
//【Android資料傳遞】Intent傳遞List和Object和List(附源碼)
//
//http://xxs4129.pixnet.net/blog/post/164402593-android-socket%E7%AF%84%E4%BE%8B
//Android Socket範例
//
//https://blog.johnsonlu.org/androidsocket/
//[Android]Sample Socket Server & Client


