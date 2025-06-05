package com.jlmorab.ms.message;

import com.jlmorab.ms.SpringApplicationCommon;

import lombok.experimental.UtilityClass;

@UtilityClass
public class Application {

	private static SpringApplicationCommon springApplication = new SpringApplicationCommon();
	
	public static void main(String[] args) {
		springApplication.init( args );
	}//end main()
	
}
