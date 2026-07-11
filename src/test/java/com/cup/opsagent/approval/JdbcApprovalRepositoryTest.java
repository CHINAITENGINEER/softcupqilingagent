package com.cup.opsagent.approval;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("postgres")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:jdbc-approval-repository-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class JdbcApprovalRepositoryTest extends ApprovalRepositoryContractTest {

    @Autowired
    private ApprovalRepository approvalRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from execution_leases");
        jdbcTemplate.update("delete from approval_records");
    }

    @Override
    protected ApprovalRepository repository() {
        return approvalRepository;
    }
}
