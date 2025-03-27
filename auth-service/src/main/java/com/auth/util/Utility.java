package com.auth.util;

public class Utility {
	public static String generateOTP() {
	    int otp = (int) (Math.random() * 900000) + 100000; // Generate a 4-digit OTP
	    return String.valueOf(otp);
	}
public static void main(String[] args) {
	System.out.println(Utility.generateOTP());
}
}

