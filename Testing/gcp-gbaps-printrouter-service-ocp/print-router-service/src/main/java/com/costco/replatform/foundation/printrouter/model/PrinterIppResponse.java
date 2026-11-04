package com.costco.replatform.foundation.printrouter.model;


public record PrinterIppResponse(int httpStatus, Integer ippStatus, int requestId, String message,boolean success,ResponseData data) {

//	public static PrinterIppResponse success(int httpStatus, int ippStatus, int requestId, String message) {
//		return new PrinterIppResponse(true, httpStatus, ippStatus, requestId, message);
//	}
//
//	public static PrinterIppResponse failure(int httpStatus, Integer ippStatus, int requestId, String message) {
//		return new PrinterIppResponse(false, httpStatus, ippStatus, requestId, message);
//	}
	
	
	
	// Inner class representing the "data" object
    public static class ResponseData {

        private boolean ok;

        private String transId;

        private int status;
        
        private String message;

        // Getters and Setters
        public boolean isOk() {
            return ok;
        }

        public void setOk(boolean ok) {
            this.ok = ok;
        }

        public String getTransId() {
            return transId;
        }

        public void setTransId(String transId) {
            this.transId = transId;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
        	if(status!=null) { //Workaround for not changing the APIGEE contract in 603 project, But in 645 needs to be Integer and null need to show in response
            this.status = status;
        	}
        }

		public String getMessage() {
			return message;
		}

		public void setMessage(String message) {
			this.message = message;
		}
        
        
    }
}
