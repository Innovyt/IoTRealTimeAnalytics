/* Copyright 2017 Innovyt

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE. */

package com.innovyt.transit.topology;

import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StormTopologyMain extends Configured implements Tool {

	private static final Logger LOG = LoggerFactory
			.getLogger(StormTopologyMain.class);

	public static void main(String[] args) {
		try {
			ToolRunner.run(new StormTopologyMain(), args);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int run(String[] args) throws Exception {
		if (args.length != 1) {
			throw new RuntimeException(
					"Expecting 1 arguement only which specifies the name of the property file");
		}
		LOG.info("property file name :" + args[0]);
		String propFileName = args[0];
		TransitEventTopology topology = new TransitEventTopology(propFileName,
				getConf());
		topology.submitTopology();

		return 0;

	}

}
