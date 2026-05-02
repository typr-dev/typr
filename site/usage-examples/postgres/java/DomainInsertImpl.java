package showcase;

import dev.typr.foundations.internal.RandomHelper;
import java.math.BigDecimal;
import java.util.Random;
import showcase.showcase.EmailAddress;
import showcase.showcase.Percentage;
import showcase.showcase.PhoneNumber;
import showcase.showcase.PositiveAmount;

public class DomainInsertImpl implements TestDomainInsert {
    @Override
    public EmailAddress showcaseEmailAddress(Random random) {
        return new EmailAddress(RandomHelper.alphanumeric(random, 8) + "@example.com");
    }

    @Override
    public PositiveAmount showcasePositiveAmount(Random random) {
        return new PositiveAmount(BigDecimal.valueOf(Math.abs(random.nextDouble() * 10000) + 1));
    }

    @Override
    public PhoneNumber showcasePhoneNumber(Random random) {
        return new PhoneNumber("+1-555-" + random.nextInt(1000) + "-" + random.nextInt(10000));
    }

    @Override
    public Percentage showcasePercentage(Random random) {
        return new Percentage(BigDecimal.valueOf(random.nextDouble() * 100));
    }
}
