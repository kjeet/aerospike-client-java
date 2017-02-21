/*
 * Copyright 2012-2017 Aerospike, Inc.
 *
 * Portions may be licensed to Aerospike, Inc. under one or more contributor
 * license agreements WHICH ARE COMPATIBLE WITH THE APACHE LICENSE, VERSION 2.0.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.aerospike.benchmarks;

import java.util.HashMap;
import java.util.Map;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Value;
import com.aerospike.client.large.LargeList;
import com.aerospike.client.large.LargeStack;
import com.aerospike.client.util.RandomShift;
import com.aerospike.client.util.Util;

public final class InsertTaskSync extends InsertTask implements Runnable {

	private final AerospikeClient client; 
	private final long keyStart;
	private final long keyCount;

	public InsertTaskSync(AerospikeClient client, Arguments args, CounterStore counters, long keyStart, long keyCount) {
		super(args, counters);
		this.client = client;
		this.keyStart = keyStart;
		this.keyCount = keyCount;
	}
	
	public void run() {
		try {			
			RandomShift random = new RandomShift();

			for (long i = 0; i < keyCount; i++) {
				try {
					runCommand(keyStart + i, random);
				}
				catch (AerospikeException ae) {
					i--;
					writeFailure(ae);
				}	
				catch (Exception e) {
					i--;
					writeFailure(e);
				}
				
				// Throttle throughput
				if (args.throughput > 0) {
					int transactions = counters.write.count.get();
					
					if (transactions > args.throughput) {
						long millis = counters.periodBegin.get() + 1000L - System.currentTimeMillis();                                        

						if (millis > 0) {
							Util.sleep(millis);
						}
					}
				}
			}
		}
		catch (Exception ex) {
			System.out.println("Insert task error: " + ex.getMessage());
			ex.printStackTrace();
		}		
	}
	
	private void runCommand(long keyCurrent, RandomShift random) {
		Key key = new Key(args.namespace, args.setName, keyCurrent);
		// Use predictable value for 0th bin same as key value
		Bin[] bins = args.getBins(random, true, keyCurrent);
		
		switch (args.storeType) {
		case KVS:
			put(key, bins);
			break;
			
		case LLIST:
			largeListAdd(key, bins[0].value);
			break;

		case LSTACK:
			largeStackPush(key, bins[0].value);
			break;
		}
	}
	
	private void put(Key key, Bin[] bins) {
		if (counters.write.latency != null) {
			long begin = System.nanoTime();
			client.put(args.writePolicy, key, bins);
			long elapsed = System.nanoTime() - begin;
			counters.write.count.getAndIncrement();			
			counters.write.latency.add(elapsed);
		}
		else {
			client.put(args.writePolicy, key, bins);
			counters.write.count.getAndIncrement();			
		}
	}

	private void largeListAdd(Key key, Value value) {
		long begin = System.nanoTime();
		if (counters.write.latency != null) {
			largeListAdd(key, value, begin);
			long elapsed = System.nanoTime() - begin;
			counters.write.count.getAndIncrement();			
			counters.write.latency.add(elapsed);
		}
		else {
			largeListAdd(key, value, begin);
			counters.write.count.getAndIncrement();			
		}
	}

	private void largeListAdd(Key key, Value value, long timestamp) {
		// Create entry
		Map<String,Value> entry = new HashMap<String,Value>();
		entry.put("key", Value.get(timestamp));
		entry.put("log", value);

		// Add entry
		LargeList list = client.getLargeList(args.writePolicy, key, "listltracker");
		list.add(Value.get(entry));
	}
		
	private void largeStackPush(Key key, Value value) {
		long begin = System.nanoTime();
		if (counters.write.latency != null) {
			largeStackPush(key, value, begin);
			long elapsed = System.nanoTime() - begin;
			counters.write.count.getAndIncrement();			
			counters.write.latency.add(elapsed);
		}
		else {
			largeStackPush(key, value, begin);
			counters.write.count.getAndIncrement();			
		}
	}
	
	private void largeStackPush(Key key, Value value, long timestamp) {
		// Create entry
		Map<String,Value> entry = new HashMap<String,Value>();
		entry.put("key", Value.get(timestamp));
		entry.put("log", value);

		// Push entry
		LargeStack lstack = client.getLargeStack(args.writePolicy, key, "stackltracker", null);
		lstack.push(Value.get(entry));
	}
}
