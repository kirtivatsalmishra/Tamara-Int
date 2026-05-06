package com.interview.dummy.murabaha.application;

import com.interview.dummy.murabaha.domain.model.RepaymentPlan;

/**
 * Template-method seam for the Sharia-compliant contract summary. A future
 * Arabic or per-region writer is a new bean, not an edit to the service.
 */
public interface ContractSummaryWriter {

    String write(RepaymentPlan plan);
}
