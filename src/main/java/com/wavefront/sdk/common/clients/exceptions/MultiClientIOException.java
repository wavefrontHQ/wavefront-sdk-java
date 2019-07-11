package com.wavefront.sdk.common.clients.exceptions;

import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.IOException;
import java.lang.Iterable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.function.Consumer;

public class MultiClientIOException implements Iterable<IOException>
{
    private final List<IOException> exceptions = new ArrayList<>();

    @Override
    public Iterator<IOException> iterator() {
        return exceptions.iterator();
    }

    @Override
    public void forEach(Consumer<? super IOException> action) {
        throw new NotImplementedException();
    }

    @Override
    public Spliterator<IOException> spliterator() {
        throw new NotImplementedException();
    }

    public void add(IOException exception) {
        this.exceptions.add(exception);
    }

    public void checkAndThrow() throws CompositeIOException {
        if (!exceptions.isEmpty())
            throw new CompositeIOException(this);
    }
}
