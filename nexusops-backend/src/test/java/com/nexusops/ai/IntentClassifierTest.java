package com.nexusops.ai;

import com.nexusops.ai.application.IntentClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {

    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new IntentClassifier();
    }

    @Test
    @DisplayName("Should classify 'create a expense reimbursement reuest' as CREATE_REQUEST even with typo")
    void testCreateExpenseReimbursementTypo() {
        IntentClassifier.Intent intent = classifier.classify("create a expense reimbursement reuest");
        assertThat(intent).isEqualTo(IntentClassifier.Intent.CREATE_REQUEST);
    }

    @Test
    @DisplayName("Should classify 'i want to reimburse my expences' as CREATE_REQUEST")
    void testReimburseExpenses() {
        IntentClassifier.Intent intent = classifier.classify("i want to reimburse my expences");
        assertThat(intent).isEqualTo(IntentClassifier.Intent.CREATE_REQUEST);
    }

    @Test
    @DisplayName("Should classify explicit policy question as POLICY_QA")
    void testExplicitPolicyQuery() {
        IntentClassifier.Intent intent = classifier.classify("what is our expense reimbursement policy?");
        assertThat(intent).isEqualTo(IntentClassifier.Intent.POLICY_QA);
    }

    @Test
    @DisplayName("Should classify hardware requests as CREATE_REQUEST")
    void testHardwareRequest() {
        IntentClassifier.Intent intent = classifier.classify("i need a high performance developer laptop");
        assertThat(intent).isEqualTo(IntentClassifier.Intent.CREATE_REQUEST);

        IntentClassifier.Intent modelIntent = classifier.classify("MacBook Pro 16 M3 Max");
        assertThat(modelIntent).isEqualTo(IntentClassifier.Intent.CREATE_REQUEST);
    }

    @Test
    @DisplayName("Should classify software access requests as CREATE_REQUEST")
    void testSoftwareRequest() {
        IntentClassifier.Intent intent = classifier.classify("request access to GitHub Copilot Enterprise");
        assertThat(intent).isEqualTo(IntentClassifier.Intent.CREATE_REQUEST);
    }
}
