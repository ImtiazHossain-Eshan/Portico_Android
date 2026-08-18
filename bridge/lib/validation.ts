import {ApiError} from "./http.js";

type JsonRecord = Record<string, unknown>;

export type PropertyBundle = {
  property: JsonRecord;
  incomeEntries: JsonRecord[];
  expenseEntries: JsonRecord[];
};

function object(value: unknown, label: string): JsonRecord {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new ApiError(400, "invalid_request", `${label} must be an object`);
  }
  return value as JsonRecord;
}

function string(value: unknown, label: string, max = 240): string {
  if (typeof value !== "string" || !value.trim() || value.length > max) {
    throw new ApiError(400, "invalid_request", `${label} is invalid`);
  }
  return value.trim();
}

function optionalString(value: unknown, label: string, max = 2_048): string {
  if (value == null || value === "") return "";
  if (typeof value !== "string" || value.length > max) {
    throw new ApiError(400, "invalid_request", `${label} is invalid`);
  }
  return value.trim();
}

function id(value: unknown, label: string): string {
  const parsed = string(value, label, 128);
  if (!/^[A-Za-z0-9_-]+$/.test(parsed)) {
    throw new ApiError(400, "invalid_request", `${label} contains unsupported characters`);
  }
  return parsed;
}

function number(value: unknown, label: string, max = 1_000_000_000_000): number {
  if (typeof value !== "number" || !Number.isFinite(value) || value < 0 || value > max) {
    throw new ApiError(400, "invalid_request", `${label} is invalid`);
  }
  return value;
}

function entry(value: unknown, kind: "income" | "expense", propertyId: string): JsonRecord {
  const source = object(value, kind);
  if (id(source.propertyId, `${kind}.propertyId`) !== propertyId) {
    throw new ApiError(400, "invalid_request", `${kind} belongs to another property`);
  }
  return {
    id: id(source.id, `${kind}.id`),
    propertyId,
    amount: number(source.amount, `${kind}.amount`),
    category: string(source.category, `${kind}.category`, 80),
    date: string(source.date, `${kind}.date`, 32),
    note: optionalString(source.note, `${kind}.note`, 500),
    recurring: source.recurring !== false,
  };
}

export function propertyBundles(value: unknown): PropertyBundle[] {
  if (!Array.isArray(value) || value.length < 1 || value.length > 25) {
    throw new ApiError(400, "invalid_request", "records must contain 1 to 25 properties");
  }

  const bundles = value.map((raw, index) => {
    const source = object(raw, `records[${index}]`);
    const propertySource = object(source.property, `records[${index}].property`);
    const propertyId = id(propertySource.id, "property.id");
    const photos = propertySource.photoUris == null ? [] : propertySource.photoUris;
    if (!Array.isArray(photos) || photos.length > 12 || photos.some((photo) => typeof photo !== "string" || photo.length > 2_048)) {
      throw new ApiError(400, "invalid_request", "property.photoUris is invalid");
    }
    const property: JsonRecord = {
      id: propertyId,
      name: string(propertySource.name, "property.name", 120),
      address: string(propertySource.address, "property.address", 300),
      country: string(propertySource.country, "property.country", 80),
      region: string(propertySource.region, "property.region", 120),
      type: string(propertySource.type, "property.type", 80),
      sizeSqm: number(propertySource.sizeSqm, "property.sizeSqm", 10_000_000),
      purchaseDate: string(propertySource.purchaseDate, "property.purchaseDate", 32),
      purchasePrice: number(propertySource.purchasePrice, "property.purchasePrice"),
      initialInvestment: number(propertySource.initialInvestment, "property.initialInvestment"),
      financingAmount: number(propertySource.financingAmount ?? 0, "property.financingAmount"),
      currentValue: number(propertySource.currentValue, "property.currentValue"),
      currency: string(propertySource.currency, "property.currency", 8).toUpperCase(),
      photoUris: photos,
      note: optionalString(propertySource.note, "property.note", 2_000),
    };
    const incomeRaw = source.incomeEntries ?? [];
    const expensesRaw = source.expenseEntries ?? [];
    if (!Array.isArray(incomeRaw) || !Array.isArray(expensesRaw) || incomeRaw.length > 100 || expensesRaw.length > 100) {
      throw new ApiError(400, "invalid_request", "Too many financial entries");
    }
    return {
      property,
      incomeEntries: incomeRaw.map((item) => entry(item, "income", propertyId)),
      expenseEntries: expensesRaw.map((item) => entry(item, "expense", propertyId)),
    };
  });

  const propertyIds = bundles.map((bundle) => bundle.property.id as string);
  if (new Set(propertyIds).size !== propertyIds.length) {
    throw new ApiError(400, "invalid_request", "Duplicate property IDs");
  }
  const writes = bundles.reduce((count, bundle) => count + 2 + bundle.incomeEntries.length + bundle.expenseEntries.length, 0);
  if (writes > 350) throw new ApiError(400, "invalid_request", "Import is too large");
  return bundles;
}

export function safeId(value: unknown, label = "id"): string {
  return id(value, label);
}
