/* Copyright 2017 Innovyt

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */

package com.innovyt.transit.bolt;

import java.util.Date;

import org.apache.storm.hdfs.bolt.rotation.FileRotationPolicy;
import org.apache.storm.tuple.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileTimeRotationPolicyImpl implements FileRotationPolicy {
	
	private static final long serialVersionUID = 1L;

	private static final Logger LOG = LoggerFactory
			.getLogger(FileTimeRotationPolicyImpl.class);

	public static enum Units {

		SECONDS((long) 1000), MINUTES((long) 1000 * 60), HOURS(
				(long) 1000 * 60 * 60), DAYS((long) 1000 * 60 * 60);

		private long milliSeconds;

		private Units(long milliSeconds) {
			this.milliSeconds = milliSeconds;
		}

		public long getMilliSeconds() {
			return milliSeconds;
		}
	}

	private long maxMilliSeconds;
	private long lastCheckpoint = new Long((new Date()).getTime());

	public FileTimeRotationPolicyImpl(float count, Units units) {
		this.maxMilliSeconds = (long) (count * units.getMilliSeconds());
	}

	@Override
	public boolean mark(Tuple tuple, long offset) {
		// The offsett is not used here as we are rotating based on time
		long diff = (new Date()).getTime() - this.lastCheckpoint;
		return diff >= this.maxMilliSeconds;
	}

	@Override
	public void reset() {
		this.lastCheckpoint = new Long((new Date()).getTime());
	}

}