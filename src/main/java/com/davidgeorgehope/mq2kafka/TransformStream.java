package com.davidgeorgehope.mq2kafka;

import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import sun.plugin.util.UserProfile;

import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

//import static io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG;

public class TransformStream {

    public Properties buildStreamsProperties(Properties envProps) {
        Properties props = new Properties();

        props.put(StreamsConfig.APPLICATION_ID_CONFIG, envProps.getProperty("application.id"));
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, envProps.getProperty("bootstrap.servers"));
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(SCHEMA_REGISTRY_URL_CONFIG, envProps.getProperty("schema.registry.url"));


        return props;
    }

    public Topology buildTopology(Properties envProps) {

        Map<String, Object> serdeProps = new HashMap<>();

        final StreamsBuilder builder = new StreamsBuilder();
        final String inputTopic = envProps.getProperty("input.topic.name");

        KStream<String, String> rawMovies = builder.stream(inputTopic);
        KStream<String, Output> movies = rawMovies.transform(()->new WAEventTransformer<>("Dave","Dave"),"Dave");

        final Serializer<Output> userProfileSerializer = new JsonPOJOSerializer<>();
        serdeProps.put("JsonPOJOClass", Output.class);
        userProfileSerializer.configure(serdeProps, false);

        final Deserializer<Output> userProfileDeserializer = new JsonPOJODeserializer<>();
        serdeProps.put("JsonPOJOClass", Output.class);
        userProfileDeserializer.configure(serdeProps, false);

        final Serde<Output> wPageViewByRegionSerde = Serdes.serdeFrom(userProfileSerializer, userProfileDeserializer);


        movies.to("movies",Produced.with(Serdes.String(), wPageViewByRegionSerde));

        return builder.build();
    }

    public static String convertRawMovie(String rawMovie) {

        return "";
    }

    public Properties loadEnvProperties(String fileName) throws IOException {
        Properties envProps = new Properties();
        FileInputStream input = new FileInputStream(fileName);
        envProps.load(input);
        input.close();

        return envProps;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            throw new IllegalArgumentException("This program takes one argument: the path to an environment configuration file.");
        }

        TransformStream ts = new TransformStream();
        Properties envProps = ts.loadEnvProperties(args[0]);
        Properties streamProps = ts.buildStreamsProperties(envProps);
        Topology topology = ts.buildTopology(envProps);

        //ts.createTopics(envProps);

        final KafkaStreams streams = new KafkaStreams(topology, streamProps);
        final CountDownLatch latch = new CountDownLatch(1);

        // Attach shutdown handler to catch Control-C.
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }
}