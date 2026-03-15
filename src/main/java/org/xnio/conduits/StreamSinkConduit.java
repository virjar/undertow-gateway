package org.xnio.conduits;

import io.netty.buffer.ByteBuf;

import java.io.IOException;

/**
 * Netty-adapted StreamSinkConduit. Replaces the XNIO StreamSinkConduit
 * in undertow-gateway. This simplified interface supports response
 * compression in the Netty-based pipeline.
 */
public interface StreamSinkConduit {

    /**
     * Process a chunk of data.
     *
     * @param data the input data (may be null when last=true to flush the final bytes)
     * @param last true if this is the final chunk
     * @return the processed output (may be empty but must not be null)
     */
    ByteBuf process(ByteBuf data, boolean last) throws IOException;

    /**
     * Release any resources held by this conduit.
     */
    void dispose();
}
