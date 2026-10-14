package dev.misteroumar.expenseflow;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ExpenseflowApplicationTests {

	@Test
	void contextLoads() {
	}

}
