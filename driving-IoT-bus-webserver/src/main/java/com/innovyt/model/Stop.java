package com.innovyt.model;

public class Stop {

	private double latitude;

	private double longitude;
	private String name;

	public double getLatitude() {
		return latitude;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return "Stop [latitude=" + latitude + ", longitude=" + longitude
				+ ", name=" + name + "]";
	}

}
