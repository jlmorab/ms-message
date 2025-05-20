package com.jlmorab.ms.message;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import com.jlmorab.ms.SpringApplicationCommon;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
class ApplicationTest {

	@Test
	void contextLoads() {
		log.info("Application deploy correctly");
	}//end contextLoads()
	
	@Test
	void executeCommonSettings() {
		try {
			SpringApplicationCommon common = mock(SpringApplicationCommon.class);
			ReflectionTestUtils.setField(Application.class, "springApplication", common);
			
			Application.main(new String[] {});
			
			verify( common, times(1) ).init( any(String[].class) );
		} finally {
			ReflectionTestUtils.setField(Application.class, "springApplication", new SpringApplicationCommon());
		}//end try
	}//end executeCommonSettings()

}
