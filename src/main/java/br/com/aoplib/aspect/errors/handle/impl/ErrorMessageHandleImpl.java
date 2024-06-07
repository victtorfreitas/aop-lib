package br.com.aoplib.aspect.errors.handle.impl;

import br.com.aoplib.aspect.errors.dto.ErrorGenericQueue;
import br.com.aoplib.aspect.errors.handle.ErrorMessageHandle;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;

@Service
@EnableJms
@Slf4j
@RequiredArgsConstructor
public class ErrorMessageHandleImpl implements ErrorMessageHandle {

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper mapper;

    @Value("${aspect.config.error.queue-name:teste}")
    private String queueName;

    @Override
    public void process(ErrorGenericQueue errorGenericQueue) {
        try {
            jmsTemplate.convertAndSend(queueName, mapper.writeValueAsString(errorGenericQueue));
            log.info("ErrorMessageHandleImpl@process - Output - Message process with success");
        } catch (RuntimeException | JsonProcessingException ex) {
            log.error("ErrorMessageHandleImpl@process - Output - Error when process message handle ex: {}", ex.getLocalizedMessage());
        }
    }
}
