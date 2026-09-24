package org.dataone.portal.servlets;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

/**
 * A ServletOutputStream that captures what a servlet writes, for assertions in tests.
 */
public class ByteArrayServletOutputStream extends ServletOutputStream {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    @Override
    public void write(int b) {
        buffer.write(b);
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setWriteListener(WriteListener writeListener) {
        throw new UnsupportedOperationException();
    }

    public String getContent() {
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }
}
