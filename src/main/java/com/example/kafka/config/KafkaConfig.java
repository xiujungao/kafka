package com.example.kafka.config;

import com.example.kafka.model.Order;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${spring.kafka.properties.sasl.mechanism:}")
    private String saslMechanism;

    @Value("${spring.kafka.properties.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.properties.ssl.endpoint.identification.algorithm:}")
    private String sslEndpointIdentificationAlgorithm;

    @Value("${spring.kafka.properties.ssl.truststore.location:}")
    private String sslTruststoreLocation;

    @Value("${spring.kafka.properties.ssl.truststore.password:}")
    private String sslTruststorePassword;

    /**
     * Resolve truststore location - handles both file paths and classpath resources
     * @param truststoreLocation The truststore location (can be file path or classpath:resource)
     * @return Absolute file path to the truststore
     */
    private String resolveTruststoreLocation(String truststoreLocation) {
        if (truststoreLocation == null || truststoreLocation.isEmpty()) {
            return null;
        }
        
        // If it's a classpath resource, extract it to a temp file
        if (truststoreLocation.startsWith("classpath:")) {
            String resourcePath = truststoreLocation.substring("classpath:".length());
            try {
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (resource.exists()) {
                    // Create a temporary file and copy the resource
                    File tempFile = File.createTempFile("kafka-truststore", ".jks");
                    tempFile.deleteOnExit();
                    
                    try (InputStream is = resource.getInputStream();
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
                        is.transferTo(fos);
                    }
                    
                    return tempFile.getAbsolutePath();
                } else {
                    System.err.println("Warning: Truststore resource not found: " + resourcePath);
                }
            } catch (Exception e) {
                System.err.println("Warning: Could not load truststore from classpath: " + e.getMessage());
            }
            return null;
        }
        
        // Otherwise, return as-is (assume it's a file path)
        return truststoreLocation;
    }

    // Producer Configuration
    @Bean
    public ProducerFactory<String, Order> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        
        // Security configuration if provided
        if (securityProtocol != null && !securityProtocol.isEmpty() && !securityProtocol.equals("PLAINTEXT")) {
            configProps.put("security.protocol", securityProtocol);
        }
        if (saslMechanism != null && !saslMechanism.isEmpty()) {
            configProps.put("sasl.mechanism", saslMechanism);
        }
        if (saslJaasConfig != null && !saslJaasConfig.isEmpty()) {
            configProps.put("sasl.jaas.config", saslJaasConfig);
        }
        
        // SSL configuration
        if (securityProtocol != null && securityProtocol.contains("SSL")) {
            // SSL truststore configuration
            String truststorePath = resolveTruststoreLocation(sslTruststoreLocation);
            
            if (truststorePath != null && !truststorePath.isEmpty()) {
                configProps.put("ssl.truststore.location", truststorePath);
                if (sslTruststorePassword != null && !sslTruststorePassword.isEmpty()) {
                    configProps.put("ssl.truststore.password", sslTruststorePassword);
                }
            }
            
            // Endpoint identification algorithm
            if (sslEndpointIdentificationAlgorithm != null) {
                configProps.put("ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
            } else {
                // Default: disable hostname verification for development
                configProps.put("ssl.endpoint.identification.algorithm", "");
            }
        }
        
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, Order> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // Consumer Configuration
    @Bean
    public ConsumerFactory<String, Order> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        configProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        configProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        configProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        configProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        
        // Performance optimizations to reduce startup delay and improve responsiveness
        // Reduce session timeout for faster rebalancing (default: 45s) - must be > 3x heartbeat
        configProps.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000); // 10 seconds
        // Reduce heartbeat interval for faster failure detection (default: 3s) - must be < session.timeout/3
        configProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 2000); // 2 seconds (less than 10s/3)
        // Reduce metadata refresh interval to detect new partitions faster (default: 5 minutes)
        configProps.put(ConsumerConfig.METADATA_MAX_AGE_CONFIG, 10000); // 10 seconds
        // Reduce fetch wait time for faster message retrieval (default: 500ms)
        configProps.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 100); // 100ms
        // Reduce min fetch bytes to get messages immediately (default: 1 byte)
        configProps.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);
        // Set max poll records for faster processing (default: 500)
        configProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        // Request timeout (default: 30s) - reduce for faster failure detection
        configProps.put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 15000); // 15 seconds
        // Max poll interval - time allowed between poll calls (default: 5 minutes)
        configProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes
        
        // Security configuration if provided
        if (securityProtocol != null && !securityProtocol.isEmpty() && !securityProtocol.equals("PLAINTEXT")) {
            configProps.put("security.protocol", securityProtocol);
        }
        if (saslMechanism != null && !saslMechanism.isEmpty()) {
            configProps.put("sasl.mechanism", saslMechanism);
        }
        if (saslJaasConfig != null && !saslJaasConfig.isEmpty()) {
            configProps.put("sasl.jaas.config", saslJaasConfig);
        }
        
        // SSL configuration
        if (securityProtocol != null && securityProtocol.contains("SSL")) {
            // SSL truststore configuration
            String truststorePath = resolveTruststoreLocation(sslTruststoreLocation);
            
            if (truststorePath != null && !truststorePath.isEmpty()) {
                configProps.put("ssl.truststore.location", truststorePath);
                if (sslTruststorePassword != null && !sslTruststorePassword.isEmpty()) {
                    configProps.put("ssl.truststore.password", sslTruststorePassword);
                }
            }
            
            // Endpoint identification algorithm
            if (sslEndpointIdentificationAlgorithm != null) {
                configProps.put("ssl.endpoint.identification.algorithm", sslEndpointIdentificationAlgorithm);
            } else {
                // Default: disable hostname verification for development
                configProps.put("ssl.endpoint.identification.algorithm", "");
            }
        }
        
        return new DefaultKafkaConsumerFactory<>(configProps, new StringDeserializer(),
                new JsonDeserializer<>(Order.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Order> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Order> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Performance optimizations for faster message consumption
        // Set to auto-start immediately
        factory.setAutoStartup(true);
        // Reduce poll timeout for faster response (default: 5s)
        factory.getContainerProperties().setPollTimeout(1000); // 1 second
        // Enable batch processing for better throughput
        factory.setBatchListener(false); // Set to true if you want batch processing
        
        return factory;
    }
}

