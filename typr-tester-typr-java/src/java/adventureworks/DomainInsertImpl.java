package adventureworks;

import adventureworks.public_.*;
import java.util.Random;
import typr.runtime.internal.RandomHelper;

public class DomainInsertImpl implements TestDomainInsert {
  @Override
  public AccountNumber publicAccountNumber(Random random) {
    return new AccountNumber(RandomHelper.alphanumeric(random, 10));
  }

  @Override
  public Flag publicFlag(Random random) {
    return new Flag(random.nextBoolean());
  }

  @Override
  public Mydomain publicMydomain(Random random) {
    return new Mydomain(RandomHelper.alphanumeric(random, 10));
  }

  @Override
  public Name publicName(Random random) {
    return new Name(RandomHelper.alphanumeric(random, 10));
  }

  @Override
  public NameStyle publicNameStyle(Random random) {
    return new NameStyle(random.nextBoolean());
  }

  @Override
  public Phone publicPhone(Random random) {
    return new Phone(RandomHelper.alphanumeric(random, 10));
  }

  @Override
  public ShortText publicShortText(Random random) {
    return new ShortText(RandomHelper.alphanumeric(random, 10));
  }

  @Override
  public OrderNumber publicOrderNumber(Random random) {
    return new OrderNumber(RandomHelper.alphanumeric(random, 10));
  }
}
