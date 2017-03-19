package com.innovyt.controller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.innovyt.model.Stop;
import com.innovyt.stop.parse.Kml;
import com.innovyt.stop.parse.Placemark;

@RestController
@RequestMapping("/transit")
public class TransitRouteStops {

	private static final Logger LOG = LoggerFactory
			.getLogger(TransitRouteStops.class);
	private static final DecimalFormat numberFormat = new DecimalFormat("#.00000");

	@RequestMapping(value = "/route/{route_id}", method = RequestMethod.GET)
	public @ResponseBody List<Stop> getRouteStops(
			@PathVariable("route_id") String route_id) {
		Stop stop = new Stop();
		List<Stop> locations = new ArrayList<Stop>();
		String routeFile = "routes/stops/" + route_id + ".kml";

		LOG.info("Getting stop sequence for route" + route_id);

		try {
			LOG.info("Processing Route File[" + routeFile + "]");

			ClassLoader classLoader = getClass().getClassLoader();
			File file = new File(classLoader.getResource(routeFile).getFile());

			JAXBContext jc = JAXBContext.newInstance(Kml.class);
			Unmarshaller u = jc.createUnmarshaller();
			Source source = new StreamSource(new FileInputStream(file));

			JAXBElement<Kml> root = u.unmarshal(source, Kml.class);

			Kml kml = root.getValue();
			String routeName = kml.getDocument().getName();

			LOG.info("Processing Route [" + routeName + "]");
			for (Placemark placemark : kml.getDocument().getPlacemark()) {
				String pointName = placemark.getName();
				String coordinates = placemark.getPoint().getCoordinates();
				String[] coord = coordinates.split(",");
				String latitude = numberFormat.format(Double.valueOf(coord[0]));
				String longitude = numberFormat
						.format(Double.valueOf(coord[1]));

				stop.setName(pointName);
				stop.setLatitude(Double.valueOf(latitude));
				stop.setLongitude(Double.valueOf(longitude));

				System.out.println(stop);

				locations.add(stop);
			}
		} catch (FileNotFoundException e) {
			String errorMessage = "Error Opening routeFile[" + routeFile + "]";
			LOG.error(errorMessage, e);
			throw new RuntimeException(errorMessage, e);
		} catch (JAXBException e) {
			String errorMessage = "JaxB exception for routeFile" + routeFile
					+ "]";
			LOG.error(errorMessage, e);
			throw new RuntimeException(errorMessage, e);
		}
		return locations;

	}

}
