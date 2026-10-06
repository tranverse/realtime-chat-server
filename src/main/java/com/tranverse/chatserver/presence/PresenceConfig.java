package com.tranverse.chatserver.presence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(PresenceProperties.class)
public class PresenceConfig {
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(name = "app.presence.enabled", havingValue = "true", matchIfMissing = true)
    static class LivePresence {
        @Bean
        RedisMessageListenerContainer presenceEvents(RedisConnectionFactory factory,
                                                     SimpMessagingTemplate messaging, ObjectMapper mapper) {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(factory);
            container.addMessageListener((message, pattern) -> {
                PresenceState state = mapper.readValue(message.getBody(), PresenceState.class);
                messaging.convertAndSend("/topic/presence/" + state.userId(), state);
            }, new ChannelTopic(RedisPresenceStore.CHANNEL));
            return container;
        }
    }
}
