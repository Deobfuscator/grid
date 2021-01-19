package com.sparrowsoftware.grid;

import java.util.function.Supplier;

public interface TaskRunner {
    <T> Promise<T> execute(Supplier<T> task);
}
