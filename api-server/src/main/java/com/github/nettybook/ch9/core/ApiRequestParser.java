package com.github.nettybook.ch9.core;

import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder.ErrorDataDecoderException;
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType;
import io.netty.util.CharsetUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class ApiRequestParser extends SimpleChannelInboundHandler<FullHttpMessage> {
    /**
     * log4j logger
     */
    private static final Logger logger = LogManager.getLogger(ApiRequestParser.class);

    private HttpRequest request;

    private JsonObject apiResult;

    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE); // Disk

    private HttpPostRequestDecoder decoder;

    private Map<String, String> reqData = new HashMap<String, String>();

    private static final Set<String> usingHeader = new HashSet<String>();
    static {
        usingHeader.add("token");
        usingHeader.add("email");
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        logger.info("요청 처리 완료");
        ctx.flush();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpMessage msg) {
        // Request header 처리.
        if (msg instanceof HttpRequest) {
            this.request = (HttpRequest) msg;


            if (HttpUtil.is100ContinueExpected(request)) {
                send100Continue(ctx);
            }

            HttpHeaders headers = request.headers();
            if (!headers.isEmpty()) {
                for (Map.Entry<String, String> h : headers) {
                    String key = h.getKey();
                    if (usingHeader.contains(key)) {
                        reqData.put(key, h.getValue());
                    }
                }
            }

            reqData.put("REQUEST_URI", request.uri());
            reqData.put("REQUEST_METHOD", request.method().name());
        }

        // Request content 처리.
        if (msg instanceof HttpContent) {
            if (msg instanceof LastHttpContent) {
                logger.debug("LastHttpContent message received!!" + request.uri());

                LastHttpContent trailer = (LastHttpContent) msg;

                readPostData();

                ApiRequest service = ServiceDispatcher.dispatch(reqData);

                try {
                    service.executeService();

                    apiResult = service.getApiResult();
                }
                finally {
                    reqData.clear();
                }

                if (!writeResponse(trailer, ctx)) {
                    // If keep-alive is off, close the connection once the
                    // content is fully written.
                    ctx.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
                }
                reset();
            }
        }
    }

    private void reset() {
        request = null;
    }

    private void readPostData() {
        try {
            decoder = new HttpPostRequestDecoder(factory, request);
            for (InterfaceHttpData data : decoder.getBodyHttpDatas()) {
                if (HttpDataType.Attribute == data.getHttpDataType()) {
                    try {
                        Attribute attribute = (Attribute) data;
                        reqData.put(attribute.getName(), attribute.getValue());
                    }
                    catch (IOException e) {
                        logger.error("BODY Attribute: " + data.getHttpDataType().name(), e);
                        return;
                    }
                }
                else {
                    logger.info("BODY data : " + data.getHttpDataType().name() + ": " + data);
                }
            }
        }
        catch (ErrorDataDecoderException e) {
            logger.error(e);
        }
        finally {
            if (decoder != null) {
                decoder.destroy();
            }
        }
    }

    private boolean writeResponse(HttpObject currentObj, ChannelHandlerContext ctx) {
        // Decide whether to close the connection or not.
        boolean keepAlive = HttpUtil.isKeepAlive(request);
        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1,
                currentObj.decoderResult().isSuccess() ? OK : BAD_REQUEST, Unpooled.copiedBuffer(
                        apiResult.toString(), CharsetUtil.UTF_8));

        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json; charset=UTF-8");

        if (keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            // Add keep alive header as per:
            // -
            // http://www.w3.org/Protocols/HTTP/1.1/draft-ietf-http-v11-spec-01.html#Connection
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // Write the response.
        ctx.write(response);

        return keepAlive;
    }

    private static void send100Continue(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
        ctx.write(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error(cause);
        ctx.close();
    }
}