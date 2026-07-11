package com.cup.opsagent.approval;

class InMemoryApprovalRepositoryTest extends ApprovalRepositoryContractTest {

    @Override
    protected ApprovalRepository repository() {
        return new InMemoryApprovalRepository();
    }
}
