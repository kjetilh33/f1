<script>
	/**
	 * @type {{ compound?: string, isNew?: boolean, age?: number }}
	 */
	let { compound = 'MEDIUM', isNew = true, age = 0 } = $props();

	/** @type {Record<string, string>} */
	const compoundStyles = {
		SOFT: 'bg-red-600 text-white border-red-500',
		MEDIUM: 'bg-yellow-400 text-black border-yellow-300 font-bold',
		HARD: 'bg-slate-100 text-slate-900 border-slate-300 font-bold',
		INTERMEDIATE: 'bg-emerald-600 text-white border-emerald-500',
		WET: 'bg-blue-600 text-white border-blue-500'
	};

	let styleClass = $derived(
		compoundStyles[compound.toUpperCase()] || 'bg-gray-700 text-white border-gray-600'
	);
	let compoundChar = $derived((compound || 'M')[0].toUpperCase());
</script>

<span
	class="inline-flex items-center gap-1 rounded border px-2 py-0.5 font-mono text-xs shadow-sm {styleClass}"
>
	<span>{compoundChar}</span>
	{#if isNew}
		<span class="rounded bg-black/20 px-1 font-sans text-[9px] tracking-widest uppercase">N</span>
	{/if}
	<span class="text-[10px] opacity-80">({age}L)</span>
</span>
