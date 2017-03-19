package com.innovyt.config;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.Environment;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.StompBrokerRelayRegistration;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.AbstractWebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig extends AbstractWebSocketMessageBrokerConfigurer {

	private static final Logger LOG = LoggerFactory.getLogger(WebSocketConfig.class);

	@Autowired
	Environment env;

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertyConfigInDev() {
		return new PropertySourcesPlaceholderConfigurer();
	}

	@NotNull
	@Value("${transit.activemq.host}")
	private String activeMQHost;

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/monitor");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		StompBrokerRelayRegistration registration = registry.enableStompBrokerRelay("/queue", "/topic");
		if (StringUtils.isEmpty(activeMQHost)) {
			String errMsg = "Property[transit.activemq.host] in application.properties must be configured for WebSocket";
			LOG.error(errMsg);
			throw new RuntimeException(errMsg);
		}
		registration.setRelayHost(activeMQHost);
		registration.setRelayPort(61613);
		registry.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void configureClientInboundChannel(ChannelRegistration inboundChannel) {

	}

	@Override
	public void configureClientOutboundChannel(ChannelRegistration out) {

	}

}
