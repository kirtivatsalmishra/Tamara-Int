# Tamara Murabaha Repayment Plan – Problem Statement

## Overview

At Tamara, we do not approach financing in the conventional sense of lending or charging interest. Instead, the model we follow is known as **Murabaha**. In this arrangement, rather than directly giving funds to a customer to acquire a commodity themselves, Tamara first undertakes the actual purchase of the commodity on behalf of the customer. After this acquisition, Tamara then resells the very same commodity to the customer, but with the resale price being higher than the initial cost, the difference being the agreed-upon profit. The customer, in turn, is expected to repay the full resale amount, which is the sum of the commodity's base cost and the profit margin, over a span of several installments. These installments are scheduled monthly, though "monthly" should be interpreted as a consistent calendar-based approach (i.e., the same date of each subsequent month) starting one month from the purchase date, rather than arbitrary day counts.

## Request Format

The request that your system will be required to handle comes in as a JSON object. Within this request will be several fields of importance. To enumerate some:

- **Customer identifier** – uniquely associates the request to a given customer
- **Base cost of the commodity** – always expressed in SAR and given to two decimal places
- **Type or category of the commodity** – examples include electronics, fashion, home goods, travel, or in some specific cases: gold, oil, metals, and wheat
- **Total number of installments** – into which the repayment is to be divided (valid range: no fewer than two installments and no greater than twelve installments)
- **Promotional code** *(optional)* – where applicable

## Profit Margin Treatment

The treatment of profit margins is central to the computation. Each commodity category has a base profit margin percentage that applies. For four specific commodities, the margins are explicitly defined:

| Commodity | Base Profit Margin |
|-----------|-------------------|
| Gold      | 8%                |
| Oil       | 10%               |
| Metals    | 12%               |
| Wheat     | 15%               |

For all other categories not falling within these four, a default margin of **12%** is to be applied.

It is also possible that a promotional code is provided in the request. If so, then, depending on the specific code, the margin itself (and only the margin, not the commodity's cost) is reduced by a certain factor:

- **SAVE10** – reduces the margin by ten percent of its value
- **SAVE20** – reduces the margin by twenty percent of its value

Regardless of any such adjustments, the margin must **never fall below 5%** after calculation. This ensures that promotions only lower the profit within reasonable bounds, never to the point of eliminating it.

## Computation Rules

Once the final profit margin percentage is known, it must be applied to the commodity cost to compute the total profit amount. This profit is then added to the original cost, giving the total amount the customer owes. This total must be divided into installments. Each installment should be represented with two decimal places of precision.

However, due to rounding, it is expected that the division may not always result in identical installment values across the entire schedule. In such cases, the **last installment should absorb the rounding adjustment** such that the overall sum of installments equals the exact total payable amount.

Importantly, the first installment should not be due immediately but **one month after the purchase date**, with subsequent installments following monthly on that same day-of-month where possible.

## Expected Output

The expected output from the system is again a JSON object. This JSON should include, among other items:

- A unique repayment plan identifier
- The identifier of the customer
- The original commodity cost
- The final profit margin percentage actually applied
- The calculated profit amount
- The total amount payable by the customer
- The base installment amount
- A complete repayment schedule detailing each installment with the due date and the amount due

Furthermore, the response must include a one-paragraph **Murabaha contract summary** written in clear, professional, and compliant language. The summary must explicitly state the cost of the commodity, the total payable, the profit included, and the number of installments, while avoiding any terminology that might suggest conventional lending or interest.

### Example Contract Summary

> "This Murabaha contract confirms our purchase of the commodity on your behalf for SAR {commodity_cost}. We are selling it to you at a total price of SAR {total_cost_to_customer}, which includes our profit of SAR {total_profit}. This amount is to be paid in {installments} equal monthly installments of SAR {installment_amount}."

## Edge Cases to Consider

A few things you'll need to consider carefully:

- Purchase amount is invalid (negative, zero, not even a number).
- Installment count is outside the valid range.
- Invalid or expired promo code.
- If the purchase date exceeds a month's length (e.g., Jan 31), set the installment to that month's last day.

This is the problem statement which we need to solve.