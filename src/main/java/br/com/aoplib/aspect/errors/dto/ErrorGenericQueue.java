package br.com.aoplib.aspect.errors.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ErrorGenericQueue {

   private String applicationName;
   private String message;
   private String correlationId;
   private LocalDateTime dateTime;
   private String stacktrace;
   private String method;
   private Map<String, JsonNode> params;
}
