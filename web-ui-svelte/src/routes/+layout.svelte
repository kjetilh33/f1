<script>
	import './layout.css';
	import favicon from '$lib/assets/favicon.svg';

	import { Navbar, NavBrand, NavLi, NavUl, NavHamburger } from "flowbite-svelte";

	let { children } = $props();
</script>

<svelte:head><link rel="icon" href={favicon} /></svelte:head>
<Navbar>
  <NavBrand href="/">
    <!-- <img src="/images/flowbite-svelte-icon-logo.svg" class="me-3 h-6 sm:h-9" alt="Flowbite Logo" /> -->
    <span class="self-center text-xl font-semibold whitespace-nowrap dark:text-white">Data</span>
  </NavBrand>
  <NavHamburger />
  <NavUl>
    <NavLi href="/">Home</NavLi>
    <NavLi href="/about">About</NavLi>
  </NavUl>
</Navbar>

{@render children()}
