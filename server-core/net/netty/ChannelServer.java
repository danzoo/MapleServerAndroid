package net.netty;

import android.content.Context;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelServer extends AbstractServer {
    private static final Logger log = LoggerFactory.getLogger(ChannelServer.class);
    private final int world;
    private final int channel;
    private Channel nettyChannel;
    private Context context;

    public ChannelServer(int port, int world, int channel, Context context) {
        super(port);
        this.world = world;
        this.channel = channel;
        this.context = context;
    }

    @Override
    public void start() {
        EventLoopGroup parentGroup = new NioEventLoopGroup();
        EventLoopGroup childGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap()
                .group(parentGroup, childGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelServerInitializer(world, channel, context));

        // Wildcard bind: dual-stack (IPv4 + IPv6) unless the JVM was started with
        // -Djava.net.preferIPv4Stack=true. See LoginServer for the full note.
        this.nettyChannel = bootstrap.bind(port).syncUninterruptibly().channel();
        log.info("Channel {}-{} bound to {}", world, channel, nettyChannel.localAddress());
    }

    @Override
    public void stop() {
        if (nettyChannel == null) {
            throw new IllegalStateException("Must start ChannelServer before stopping it");
        }

        nettyChannel.close().syncUninterruptibly();
    }
}
