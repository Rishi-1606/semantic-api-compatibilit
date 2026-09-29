/**
 * JsonBlock
 * Pretty-prints a JSON string (or raw object) in a styled <pre> block.
 *
 * @param {string|object} value  JSON string or object to display
 * @param {string}        label  Optional label above the block
 */
export default function JsonBlock({ value, label }) {
  let display;
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    display = JSON.stringify(parsed, null, 2);
  } catch {
    display = typeof value === 'string' ? value : String(value);
  }

  return (
    <div className="json-block">
      {label && <span className="label">{label}</span>}
      <pre className="json-pre">{display}</pre>
    </div>
  );
}
