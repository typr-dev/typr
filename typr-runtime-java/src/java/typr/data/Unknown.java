package typr.data;

// A column type typo doesn't know how to handle. it'll be cast to/from `text`
public record Unknown(String value) {}
