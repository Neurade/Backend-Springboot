package app.demo.neurade.configs;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    public static final String CHATBOT_QUEUE = "chatbot.processing.queue";
    public static final String CHATBOT_DLQ = "chatbot.processing.dlq";
    public static final String CHATBOT_EXCHANGE = "chatbot.processing.exchange";
    public static final String CHATBOT_ROUTING_KEY = "chatbot.process";

    public static final String ASSIGNMENT_QUEUE = "assignment.judge.queue";
    public static final String ASSIGNMENT_DLQ = "assignment.judge.dlq";
    public static final String ASSIGNMENT_EXCHANGE = "assignment.judge.exchange";
    public static final String ASSIGNMENT_ROUTING_KEY = "assignment.judge";

    public static final String USER_CREATED_QUEUE = "user.created.queue";
    public static final String USER_CREATED_DLQ = "user.created.dlq";
    public static final String USER_CREATED_EXCHANGE = "user.created.exchange";
    public static final String USER_CREATED_ROUTING_KEY = "user.created";

    @Value("${spring.rabbitmq.listener.simple.concurrency}")
    private int listenerConcurrency;

    @Value("${spring.rabbitmq.listener.simple.max-concurrency}")
    private int listenerMaxConcurrency;

    @Value("${spring.rabbitmq.listener.simple.prefetch}")
    private int listenerPrefetch;

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(listenerConcurrency);
        factory.setMaxConcurrentConsumers(listenerMaxConcurrency);
        factory.setPrefetchCount(listenerPrefetch);
        factory.setMessageConverter(messageConverter);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    public Queue chatbotQueue() {
        return QueueBuilder.durable(CHATBOT_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", CHATBOT_DLQ)
                .build();
    }

    @Bean
    public Queue chatbotDLQ() {
        return QueueBuilder.durable(CHATBOT_DLQ).build();
    }

    @Bean
    public Exchange chatbotExchange() {
        return new DirectExchange(CHATBOT_EXCHANGE);
    }

    @Bean
    public Binding chatbotBinding(Queue chatbotQueue, Exchange chatbotExchange) {
        return BindingBuilder.bind(chatbotQueue)
                .to(chatbotExchange)
                .with(CHATBOT_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Queue assignmentQueue() {
        return QueueBuilder.durable(ASSIGNMENT_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", ASSIGNMENT_DLQ)
                .build();
    }

    @Bean
    public Queue assignmentDLQ() {
        return QueueBuilder.durable(ASSIGNMENT_DLQ).build();
    }

    @Bean
    public Exchange assignmentExchange() {
        return new DirectExchange(ASSIGNMENT_EXCHANGE);
    }

    @Bean
    public Binding assignmentBinding(Queue assignmentQueue, Exchange assignmentExchange) {
        return BindingBuilder.bind(assignmentQueue)
                .to(assignmentExchange)
                .with(ASSIGNMENT_ROUTING_KEY)
                .noargs();
    }

    @Bean
    public Queue userCreatedQueue() {
        return QueueBuilder.durable(USER_CREATED_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", USER_CREATED_DLQ)
                .build();
    }

    @Bean
    public Queue userCreatedDLQ() {
        return QueueBuilder.durable(USER_CREATED_DLQ).build();
    }

    @Bean
    public Exchange userCreatedExchange() {
        return new DirectExchange(USER_CREATED_EXCHANGE);
    }

    @Bean
    public Binding userCreatedBinding(Queue userCreatedQueue, Exchange userCreatedExchange) {
        return BindingBuilder.bind(userCreatedQueue)
                .to(userCreatedExchange)
                .with(USER_CREATED_ROUTING_KEY)
                .noargs();
    }
}
