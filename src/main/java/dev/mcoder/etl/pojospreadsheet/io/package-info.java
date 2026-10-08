/**
 * Reading spreadsheets into POJOs and writing POJOs to spreadsheets.
 *
 * <ul>
 *   <li>{@link dev.mcoder.etl.pojospreadsheet.io.SpreadsheetReader} reads and validates rows,
 *       configured by {@link dev.mcoder.etl.pojospreadsheet.io.ReadOptions}. Invalid input is
 *       reported as a {@link dev.mcoder.etl.pojospreadsheet.io.SpreadsheetValidationException}
 *       holding one {@link dev.mcoder.etl.pojospreadsheet.io.RowError} per problem.</li>
 *   <li>{@link dev.mcoder.etl.pojospreadsheet.io.SpreadsheetWriter} writes rows, configured by
 *       {@link dev.mcoder.etl.pojospreadsheet.io.WriteOptions}, in a
 *       {@link dev.mcoder.etl.pojospreadsheet.io.SpreadsheetFormat}.</li>
 *   <li>{@link dev.mcoder.etl.pojospreadsheet.io.SheetMetadata} lists a POJO's column mapping.</li>
 * </ul>
 */
package dev.mcoder.etl.pojospreadsheet.io;
