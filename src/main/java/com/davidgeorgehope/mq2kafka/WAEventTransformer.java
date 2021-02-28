package com.davidgeorgehope.mq2kafka;

import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.Transformer;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WAEventTransformer<T> implements Transformer<String, String, KeyValue<String, Output>> {
	private static final Logger LOGGER = LoggerFactory.getLogger(WAEventTransformer.class);
	
	private final String stateStoreName ;
	private final String dataUpdater;

	private KeyValueStore<String, Integer> store;

	public WAEventTransformer(String stateStoreName, String dataUpdater) {
		this.stateStoreName = stateStoreName;
		this.dataUpdater = dataUpdater;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void init(ProcessorContext context) {
		store = (KeyValueStore<String, Integer>) context.getStateStore(stateStoreName);
	}

	@Override
	public KeyValue<String, Output> transform(String key, String event) {
		LOGGER.trace("Processing {}.", event);

		Integer stored = store.get(key);

		if (stored == null) {
			stored = 0;
		}else{
			stored=++stored;
		}

		store.put(key, stored);

		Output output = new Output();
		output.setValue(event);
		output.setVersion(stored);

		return new KeyValue<>(key, output);
	}

	@Override
	public void close() {
		// Do nothing
	}
}
