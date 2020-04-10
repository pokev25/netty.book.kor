package com.github.nettybook.ch7.junit;

import java.util.Date;

/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Handles a server-side channel.
 */
@Sharable

public class TelnetServerHandlerV3 extends SimpleChannelInboundHandler<String> {


    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // Send greeting for a new connection.
        ctx.write(ResponseGenerator.makeHello());
        ctx.flush();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, String request)
            throws Exception {
        ResponseGenerator generator = new ResponseGenerator(request);
        
        // Generate and write a response.
        String response = generator.response();

        // We do not need to write a ChannelBuffer here.
        // We know the encoder inserted at TelnetPipelineFactory will do the
        // conversion.
        ChannelFuture future = ctx.write(response);

        // Close the connection after sending 'Have a good day!'
        // if the client has sent 'bye'.
        if (generator.isClose()) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
    
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            
          
            if(e.state() == IdleState.ALL_IDLE){
            	ctx.writeAndFlush("All IDLE "+ new Date().toString() +"\r\n");
            	//log.info("All " + e.toString());
            }else if(e.state() == IdleState.READER_IDLE){
            	//log.info("Read " +e.toString());
            	ctx.writeAndFlush("READER IDLE "+ new Date().toString() +"\r\n");
            }else if(e.state() == IdleState.WRITER_IDLE){
            	//log.info("Write " +e.toString());
            	ctx.writeAndFlush("WRITER IDLE"+ new Date().toString() +" \r\n");
            }else{
            	//log.info("What the " +e.toString());
            	ctx.writeAndFlush(" IDLE \n");
            }
            
            
        }
    }
}
