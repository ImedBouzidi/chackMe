# Billing module (Chess Platform roadmap, section 38)

Copy `src/main/java/com/chessplatform/billing` and the resources into game-service.
Add `@ConfigurationPropertiesScan` to the application class and import `application-billing.yml`.

Status: written against current Spring Boot 3.2+, Google Play Developer API and stripe-java
conventions; NOT compiled or run. Pin dependency versions, let the compiler check the Google
and Stripe client class names, and run the tests listed in section 38.15 before shipping.
