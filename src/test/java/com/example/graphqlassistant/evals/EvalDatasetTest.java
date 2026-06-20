package com.example.graphqlassistant.evals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalDatasetTest {

  @Test
  void loadsEveryRequiredEvaluationCategory() {
    List<EvalCase> cases = EvalDataset.loadAll();

    assertThat(cases).extracting(EvalCase::category).contains("generation", "troubleshooting");
    assertThat(cases)
        .extracting(EvalCase::expectedError)
        .contains("CLARIFICATION", "INVALID_RESPONSE");
    assertThat(cases)
        .anySatisfy(
            evalCase -> {
              assertThat(evalCase.prompt()).containsIgnoringCase("ignore");
              assertThat(evalCase.category()).isEqualTo("adversarial");
            });
    assertThat(cases)
        .anySatisfy(
            evalCase ->
                assertThat(evalCase.requiredTools())
                    .contains("inspectSchema", "validateOperation"));
  }
}
