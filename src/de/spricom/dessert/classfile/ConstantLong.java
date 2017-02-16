package de.spricom.dessert.classfile;

class ConstantLong extends ConstantPoolEntry {
	public static final int TAG = 5;
	private final long value;

	public ConstantLong(long value) {
		this.value = value;
	}

	@Override
	public String dump(ClassFile cf) {
		return "long: " + value;
	}

	public long getValue() {
		return value;
	}
}
