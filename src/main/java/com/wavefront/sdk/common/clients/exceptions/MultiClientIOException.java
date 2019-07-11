package com.wavefront.sdk.common.clients.exceptions;

import java.io.IOException;
import java.lang.Iterable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MultiClientIOException implements Iterable<IOException>
{
    private final List<IOException> exceptions = new ArrayList<>();

    @Override
    public Iterator<IOException> iterator() {
        return exceptions.iterator();
    }

    public void add(IOException exception) {
        this.exceptions.add(exception);
    }

    public void checkAndThrow() throws CompositeIOException {
        if (!exceptions.isEmpty())
            throw new CompositeIOException(this);
    }
}
