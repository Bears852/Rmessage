package io.rector.netty.core;

import io.reactor.netty.api.codec.Protocol;
import io.reactor.netty.api.exception.NotFindConfigException;
import io.rector.netty.config.ServerConfig;
import io.rector.netty.core.session.ServerSession;
import io.rector.netty.core.session.TcpServerSession;
import io.rector.netty.flow.plugin.FrameInterceptor;
import io.rector.netty.flow.plugin.PluginRegistry;
import io.rector.netty.flow.plugin.Plugins;
import io.rector.netty.transport.ServerTransport;
import io.rector.netty.transport.distribute.DirectOfflineMessageDistribute;
import io.rector.netty.transport.distribute.OfflineMessageDistribute;
import io.rector.netty.transport.method.ReactorMethodExtend;
import io.rector.netty.transport.socket.RsocketAcceptor;
import io.rector.netty.transport.socket.ServerSocketAdapter;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.UnicastProcessor;
import reactor.ipc.netty.NettyConnector;
import reactor.ipc.netty.NettyInbound;
import reactor.ipc.netty.NettyOutbound;
import reactor.ipc.netty.tcp.TcpServer;
import reactor.util.Logger;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;


/**
 * @Auther: lxr
 * @Date: 2018/12/7 17:27
 * @Description:
 */
@Data
@Slf4j
public class ServerStart extends AbstractStart {

    private static UnicastProcessor<Operation> operations =UnicastProcessor.create();

    private Consumer<Map<Protocol,Class<? extends NettyConnector>>> consumer = classes-> classes.put(Protocol.TCP,TcpServer.class);

    private PluginRegistry registry = Plugins.defaultPlugins();


    public ServerStart() {
        super(ServerConfig.builder().build(), ReactorMethodExtend.builder().offlineMessageDistribute(new DirectOfflineMessageDistribute()).build());
    }



    private static class StartBuilder{
        private static ServerStart start = new ServerStart();
    }


    public static ServerStart builder(){
        return StartBuilder.start;
    }


    public  ServerStart offlineHandler(OfflineMessageDistribute distribute){
        methodExtend.setOfflineMessageDistribute(distribute);
        return this;
    }


    @Override
    public Start interceptor(FrameInterceptor... frameInterceptor) {
        registry.addServerPlugin(frameInterceptor);
        return this;
    }


    @SuppressWarnings("unchecked")
    public <T extends NettyConnector< ? extends NettyInbound,? extends NettyOutbound>> Mono<ServerSession<T>> connect(){

        ServerTransport<T> serverTransport =new ServerTransport(socketFactory()
                .accept(consumer)
                .getSocket(config.getProtocol())
                .orElseThrow(()->new NotFindConfigException("协议不存在")));
        return rsocketAcceptor()
                .map(rsocketAcceptor -> {
                      ServerSocketAdapter<T> rsocket= (ServerSocketAdapter<T> )rsocketAcceptor.accept(() -> serverTransport,registry,(ServerConfig)config,methodExtend);
                         return   rsocket.start()
                                 .map(socket->new TcpServerSession(rsocket))
                                 .doOnError(ex-> {
                                     serverTransport.close().subscribe();
                                     log.error("connect error:",ex);
                                 })
                                 .log("server")
                                 .retry()
                                 .block();
                });
    }

    private  Mono<RsocketAcceptor>  rsocketAcceptor(){
        return Mono.just(ServerSocketAdapter::new);
    }



    public static void  main(String[] a) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
          ServerStart
                .builder()
                .tcp()
                .ip("127.0.0.1")
                .port(1888)
                .interceptor(frame -> frame,frame -> frame)
                .setAfterChannelInit(channel -> {

                    //  channel设置
                })
                .<TcpServer>connect().subscribe(session->{
                    session.addOfflineHandler(()->new DirectOfflineMessageDistribute());

                });
        byte b =117;
        int high= b>>4 & 0x0F ;
        int low= b & 0x0F ;
        System.out.println("high:"+high);
        System.out.println("low:"+low);
        countDownLatch.await();
    }







}
