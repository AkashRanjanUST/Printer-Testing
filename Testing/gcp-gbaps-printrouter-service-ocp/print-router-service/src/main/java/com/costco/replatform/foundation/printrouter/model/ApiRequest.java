package com.costco.replatform.foundation.printrouter.model;

public class ApiRequest<T> {
	
	  private T request;
	  private String requestId;
	  private String source;
	  private String timestamp;
	public T getRequest() {
		return request;
	}
	public void setRequest(T request) {
		this.request = request;
	}
	public String getRequestId() {
		return requestId;
	}
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
	public String getSource() {
		return source;
	}
	public void setSource(String source) {
		this.source = source;
	}
	public String getTimestamp() {
		return timestamp;
	}
	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	
	
	

}
