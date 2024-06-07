package br.com.aoplib.aspect.errors.handle;

import br.com.aoplib.aspect.errors.dto.ErrorGenericQueue;

public interface ErrorMessageHandle {

    void process(ErrorGenericQueue errorGenericQueue);
}
