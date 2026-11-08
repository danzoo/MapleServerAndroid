package net.netty;

import android.content.Context;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import net.server.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoginServer extends AbstractServer {
    private static final Logger log = LoggerFactory.getLogger(LoginServer.class);
    public static final int WORLD_ID = -1;
    public static final int CHANNEL_ID = -1;
    private Channel channel;
    public LoginServer(int port) {
        super(port);
    }

    @Override
    public void start() {
        EventLoopGroup parentGroup = new NioEventLoopGroup();
        EventLoopGroup childGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new LoginServerInitializer(Server.getInstance().getContext()));

        // bind(int) is a wildcard bind. On a dual-stack JVM that is "[::]:port" and accepts IPv4 as
        // well as IPv6 clients; with -Djava.net.preferIPv4Stack=true it would be "0.0.0.0:port"
        // and IPv6 clients would be refused. Log it so the family is visible at startup.
        this.channel = bootstrap.bind(port).syncUninterruptibly().channel();
        log.info("Login server bound to {}", channel.localAddress());
    }

    @Override
    public void stop() {
        if (channel == null) {
            throw new IllegalStateException("Must start LoginServer before stopping it");
        }

        channel.close().syncUninterruptibly();
    }
}
