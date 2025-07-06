package thong.test.customerpointservice.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import thong.test.customerpointservice.mapper.ModifyPointEventRecordDeserializer;
import thong.test.customerpointservice.pojo.ModifyPointEventRecord;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConsumerFactoryConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerFactoryConfig.class);

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapAddress;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, ModifyPointEventRecord<?>> modifyPointEventRecordConsumerFactoryConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapAddress);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ModifyPointEventRecordDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ModifyPointEventRecord<?>> modifyPointEventRecordConsumerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, ModifyPointEventRecord<?>> factory
                = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(modifyPointEventRecordConsumerFactoryConfig());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.getContainerProperties().setSyncCommits(true);
        factory.setRecordInterceptor((record, consumer) -> {
            log.info("Intercepted message: {}", record.value());
            return record;
        });

        return factory;
    }
}
