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
    assertThat(cases)
        .filteredOn(evalCase -> evalCase.category().equals("generation"))
        .extracting(EvalCase::prompt)
        .containsExactly(
            "generate the query to get the list of country",
            "generate the query to get the country by code");
    assertThat(cases)
        .extracting(EvalCase::id)
        .contains(
            "regression-valid-multiline-query",
            "regression-multiline-invalid-fields",
            "regression-one-line-argument-syntax");
    assertThat(EvalDataset.loadLive())
        .extracting(EvalCase::id)
        .contains(
            "live-regression-generation-list",
            "live-regression-generation-country-by-code",
            "live-regression-valid-multiline-query",
            "live-regression-multiline-invalid-fields",
            "live-regression-one-line-argument-syntax");
  }
}
